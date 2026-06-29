package com.cyx.zsfxpt.config.ThreadPool;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;


//创建一个线程池注册为bean
@Configuration
public class ThreadPoolConfig {
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);//核心线程是10
        executor.setMaxPoolSize(50);//最大线程是50
        executor.setQueueCapacity(200);//任务队列容量为200
        executor.setKeepAliveSeconds(30);//非核心线程空闲存活时间是 30 秒。
        executor.setThreadNamePrefix("NoteExecutor-");//线程名前缀
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());//拒绝策略：如果线程池满了，队列也满了，新任务不会直接丢掉，而是让“提交任务的那个线程”自己去执行这个任务。
        executor.setWaitForTasksToCompleteOnShutdown(true);//项目停止的时候，不会立刻强行杀掉线程池里的任务，而是尽量让已经提交的任务执行完。
        executor.setAwaitTerminationSeconds(60);//关闭项目时，最多等线程池里的任务执行 60 秒。如果 60 秒还没执行完，就继续关闭流程。
        executor.initialize();//初始化线程池。
        return executor;
    }
}