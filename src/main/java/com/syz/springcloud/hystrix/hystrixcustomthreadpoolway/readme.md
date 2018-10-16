上面介绍了改造线程和线程池的方式，并且通过建一个同样的Java类来覆盖Jar包中的实现，感觉有点投机取巧，其实不用这么麻烦，Hystrix默认提供了HystrixPlugins类，可以让用户自定义线程池，下面来看看怎么使用：

在启动之前调用进行注册自定义实现的逻辑：

HystrixPlugins.getInstance().registerConcurrencyStrategy(new ThreadLocalHystrixConcurrencyStrategy());



Executor：是Java线程池的超级接口；提供一个execute(Runnable command)方法;我们一般用它的继承接口ExecutorService。
Executors：是java.util.concurrent包下的一个类，提供了若干个静态方法，用于生成不同类型的线程池。Executors一共可以创建下面这四类线程池：

newFixedThreadPool创建一个可缓存线程池，如果线程池长度超过处理需要，可灵活回收空闲线程，若无可回收，则新建线程。
newFixedThreadPool 创建一个定长线程池，可控制线程最大并发数，超出的线程会在队列中等待。
newScheduledThreadPool 创建一个线程池，它可安排在给定延迟后运行命令或者定期地执行。
newSingleThreadExecutor 创建一个使用单个 worker 线程的 Executor，以无界队列方式来运行该线程。它只会用唯一的工作线程来执行任务，保证所有任务按照指定顺序(FIFO, LIFO, 优先级)执行。
ExecutorService：它是线程池定义的一个接口，继承Executor。有两个实现类，分别为ThreadPoolExecutor,ScheduledThreadPoolExecutor。
execute方法：方法接收一个Runnable实例，并且异步的执行，请看下面的实例：

