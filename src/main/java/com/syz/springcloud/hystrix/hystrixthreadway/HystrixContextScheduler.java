package com.syz.springcloud.hystrix.hystrixthreadway;


import com.alibaba.ttl.TtlRunnable;
import com.netflix.hystrix.HystrixThreadPool;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.concurrency.HystrixConcurrencyStrategy;
import com.netflix.hystrix.strategy.concurrency.HystrixContexSchedulerAction;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Func0;
import rx.internal.schedulers.ScheduledAction;
import rx.subscriptions.CompositeSubscription;
import rx.subscriptions.Subscriptions;

import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/*
 * ========================================
 * @Author:       syz
 * @Description:  改造线程的方式，在Hystrix将命令丢入线程池的时候对线程进行修饰也可以解决此问题，因为ransmittable-thread-local对线程池进行修饰，其原理也是改造了线程
 * @Date:        Create in  2018-10-16 10:43:48
 * @Modify By:
 * ========================================
 **/
public class HystrixContextScheduler extends Scheduler {

    //HystrixContextScheduler:用于在执行命令的时候，把命令在这个线程池上调度执行
    //可以通过HystrixContextScheduler进行入手，Hystrix通过HystrixContextScheduler的ThreadPoolScheduler把命令submit到ThreadPoolExecutor中去执行。
    //  Scheduler 线程调度
   /* 默认情况下rxjava中生产者和订阅者都是在当前线程中执行。Scheduler就是用来切换生产者和订阅者执行的线程。
   Scheduler默认内置了一些内置的Scheduler，方便针对不同的任务选择.*/
    //ThreadPoolExecutor中一个线程就是一个Worker对象，它与一个线程绑定，当Worker执行完毕就是线程执行完毕
    private final HystrixConcurrencyStrategy concurrencyStrategy;  //Hystrix的线程池创建源码是在HystrixConcurrencyStrategy类getThreadPool方法中实现的
    private final Scheduler actualScheduler;  //线程调度
    private final HystrixThreadPool threadPool;

    public HystrixContextScheduler(Scheduler scheduler) {
        this.actualScheduler = scheduler;
        this.concurrencyStrategy = HystrixPlugins.getInstance().getConcurrencyStrategy();
        this.threadPool = null;
    }

    public HystrixContextScheduler(HystrixConcurrencyStrategy concurrencyStrategy, Scheduler scheduler) {
        this.actualScheduler = scheduler;
        this.concurrencyStrategy = concurrencyStrategy;
        this.threadPool = null;
    }

    public HystrixContextScheduler(HystrixConcurrencyStrategy concurrencyStrategy, HystrixThreadPool threadPool) {
        this(concurrencyStrategy, threadPool, new Func0<Boolean>() {
            @Override
            public Boolean call() {
                return true;
            }
        });
    }

    public HystrixContextScheduler(HystrixConcurrencyStrategy concurrencyStrategy, HystrixThreadPool threadPool, Func0<Boolean> shouldInterruptThread) {
        this.concurrencyStrategy = concurrencyStrategy;
        this.threadPool = threadPool;
        this.actualScheduler = new ThreadPoolScheduler(threadPool, shouldInterruptThread);
    }

    @Override
    public Worker createWorker() {
        return new HystrixContextSchedulerWorker(actualScheduler.createWorker());
    }

    private class HystrixContextSchedulerWorker extends Worker {

        private final Worker worker;

        private HystrixContextSchedulerWorker(Worker actualWorker) {
            this.worker = actualWorker;
        }

        @Override
        public void unsubscribe() {
            worker.unsubscribe();
        }

        @Override
        public boolean isUnsubscribed() {
            return worker.isUnsubscribed();
        }

        @Override
        public Subscription schedule(Action0 action, long delayTime, TimeUnit unit) {
            if (threadPool != null) {
                if (!threadPool.isQueueSpaceAvailable()) {
                    throw new RejectedExecutionException("Rejected command because thread-pool queueSize is at rejection threshold.");
                }
            }
            return worker.schedule(new HystrixContexSchedulerAction(concurrencyStrategy, action), delayTime, unit);
        }

        @Override
        public Subscription schedule(Action0 action) {
            if (threadPool != null) {
                if (!threadPool.isQueueSpaceAvailable()) {
                    throw new RejectedExecutionException("Rejected command because thread-pool queueSize is at rejection threshold.");
                }
            }
            return worker.schedule(new HystrixContexSchedulerAction(concurrencyStrategy, action));
        }

    }

    private static class ThreadPoolScheduler extends Scheduler {

        private final HystrixThreadPool threadPool;
        private final Func0<Boolean> shouldInterruptThread;

        public ThreadPoolScheduler(HystrixThreadPool threadPool, Func0<Boolean> shouldInterruptThread) {
            this.threadPool = threadPool;
            this.shouldInterruptThread = shouldInterruptThread;
        }

        @Override
        public Worker createWorker() {
            return new ThreadPoolWorker(threadPool, shouldInterruptThread);
        }

    }

    /**
     * Purely for scheduling work on a thread-pool.
     * <p>
     * This is not natively supported by RxJava as of 0.18.0 because thread-pools
     * are contrary to sequential execution.
     * <p>
     * For the Hystrix case, each Command invocation has a single action so the concurrency
     * issue is not a problem.
     */
    private static class ThreadPoolWorker extends Worker {

        private final HystrixThreadPool threadPool;
        private final CompositeSubscription subscription = new CompositeSubscription();
        private final Func0<Boolean> shouldInterruptThread;

        public ThreadPoolWorker(HystrixThreadPool threadPool, Func0<Boolean> shouldInterruptThread) {
            this.threadPool = threadPool;
            this.shouldInterruptThread = shouldInterruptThread;
        }

        @Override
        public void unsubscribe() {
            subscription.unsubscribe();
        }

        @Override
        public boolean isUnsubscribed() {
            return subscription.isUnsubscribed();
        }

        @Override
        public Subscription schedule(final Action0 action) {
            if (subscription.isUnsubscribed()) {
                // don't schedule, we are unsubscribed
                return Subscriptions.unsubscribed();
            }

            // This is internal RxJava API but it is too useful.
            ScheduledAction sa = new ScheduledAction(action);

            subscription.add(sa);
            sa.addParent(subscription);
            ThreadPoolExecutor executor = (ThreadPoolExecutor) threadPool.getExecutor();
            FutureTask<?> f = (FutureTask<?>) executor.submit(TtlRunnable.get(sa));
            sa.add(new FutureCompleterWithConfigurableInterrupt(f, shouldInterruptThread, executor));

            return sa;
        }

        @Override
        public Subscription schedule(Action0 action, long delayTime, TimeUnit unit) {
            throw new IllegalStateException("Hystrix does not support delayed scheduling");
        }
    }

    /**
     * Very similar to rx.internal.schedulers.ScheduledAction.FutureCompleter, but with configurable interrupt behavior
     */
    private static class FutureCompleterWithConfigurableInterrupt implements Subscription {
        private final FutureTask<?> f;
        private final Func0<Boolean> shouldInterruptThread;
        private final ThreadPoolExecutor executor;

        private FutureCompleterWithConfigurableInterrupt(FutureTask<?> f, Func0<Boolean> shouldInterruptThread, ThreadPoolExecutor executor) {
            this.f = f;
            this.shouldInterruptThread = shouldInterruptThread;
            this.executor = executor;
        }

        @Override
        public void unsubscribe() {
            executor.remove(f);
            if (shouldInterruptThread.call()) {
                f.cancel(true);
            } else {
                f.cancel(false);
            }
        }

        @Override
        public boolean isUnsubscribed() {
            return f.isCancelled();
        }
    }
}
