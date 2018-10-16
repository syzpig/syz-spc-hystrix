package com.syz.springcloud.hystrix.hystrixthreadpoolway;

import com.alibaba.ttl.TtlRunnable;

import java.util.concurrent.*;

/*
 * ========================================
 * @Author:       syz
 * @Description:  线程池的方式解决
 * @Date:        Create in  2018-10-16 10:27:13
 * @Modify By:
 * ========================================
 **/
public class ThreadLocalThreadPoolExecutor extends ThreadPoolExecutor{//ExecutorService是Executor的子接口，增加了一些常用的对线程的控制方法，之后使用线程池主要也是使用这些方法。

    // RejectedExecutionHandler 线程池--拒绝策略
    // 当线程池的任务缓存队列已满并且线程池中的线程数目达到maximumPoolSize，如果还有任务到来就会采取任务拒绝策略，通常有以下四种策略：
   /* ThreadPoolExecutor.AbortPolicy:丢弃任务并抛出RejectedExecutionException异常。
      ThreadPoolExecutor.DiscardPolicy：也是丢弃任务，但是不抛出异常。
      ThreadPoolExecutor.DiscardOldestPolicy：丢弃队列最前面的任务，然后重新尝试执行任务（重复此过程）
      ThreadPoolExecutor.CallerRunsPolicy：由调用线程处理该任务*/
    private static final RejectedExecutionHandler defaultHandler = new AbortPolicy();

    public ThreadLocalThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }


    public ThreadLocalThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, defaultHandler);
    }

    @Override
    public void execute(Runnable command) {
        super.execute(TtlRunnable.get(command));
    }

}
