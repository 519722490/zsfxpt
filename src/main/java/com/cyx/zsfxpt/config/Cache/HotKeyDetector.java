package com.cyx.zsfxpt.config.Cache;

import com.cyx.zsfxpt.config.Cache.CacheProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 热键探测器（滑动时间窗口计数 + 热度分级 + TTL 动态扩展）。
 * <p>
 * 设计说明：
 * - 采用固定分段滑动窗口：窗口长度 windowSeconds，分段长度 segmentSeconds，段数 segments=window/segment；
 * - 每个 key 维护长度为 segments 的数组 counters[key]，current 指向当前活跃段；
 * - 周期性 rotate 将 current 前移并清零新段，实现近窗口热度的自然衰减；
 * - 根据总热度 h=Σ段计数，映射到 NONE/LOW/MEDIUM/HIGH 的热度等级；
 * - 提供 ttlForPublic/ttlForMine：在基准 TTL 上叠加等级扩展秒数，保护热点请求。
 * <p>
 * 并发语义：
 * - 使用 ConcurrentHashMap 存储计数数组，AtomicInteger 维护段游标；
 * - 计数递增为无锁数组操作，rotate 仅清零新段，避免大范围写冲突；
 * - 统计为近似滑窗，保证在高并发下的稳定与低开销。
 */
@Component
public class HotKeyDetector {
    public enum Level { NONE, LOW, MEDIUM, HIGH }

    /** 缓存配置（包含窗口/分段参数、等级阈值、扩展秒数） */
    private final CacheProperties properties;
    /** 每个 key 的滑窗分段计数数组，长度为 segments */
    private final Map<String, int[]> counters = new ConcurrentHashMap<>();
    //这个map虽然用ConcurrentHashMap保证了这个map的value读取线程安全，但是value里面的int[]不是线程安全的，也就是说也有可能发生线程安全问题，但是好像不太重要。
    /** 当前活跃分段索引（原子维护） */
    private final AtomicInteger current = new AtomicInteger(0);
    /** 滑窗分段数量：windowSeconds / segmentSeconds */
    private final int segments;
    //解释一下这个流程，首先滑动窗口的最大检测范围是60s，然后本项目的热度统计设计为分段统计，就是将这60s分成好几个段，比如10s一个段单独统计热度
    //可以理解为不粗暴的记录当前滑动窗口的总浏览数，而是在滑动窗口的里面再设计一个滑动窗口
    //current记录当前滑到哪个分段了，这个current就是10s一次++，超过范围了就回到数组头部，这样覆盖了第一段就是天然的60s滑动窗口
    //map的key是内容的标识符，value的int[]就是那个滑动窗口的访问频率计数

    /**
     * 初始化探测器：根据配置计算分段数量。
     * @param properties 缓存配置（hotkey）
     */
    public HotKeyDetector(CacheProperties properties) {
        this.properties = properties;
        int segSeconds = properties.getHotkey().getSegmentSeconds();
        int winSeconds = properties.getHotkey().getWindowSeconds();
        this.segments = Math.max(1, winSeconds / Math.max(1, segSeconds));
    }

    /**
     * 记录一次访问，将计数累加到当前分段。
     * @param key 缓存键
     */
    public void record(String key) {
        int[] arr = counters.computeIfAbsent(key, k -> new int[segments]);//如果当前key还没有进行缓存，就新建一个长度为分段数的数组
        arr[current.get()]++;//当前时间分段的访问数++
        //这里就有可能有线程安全问题：因为int数组不是线程安全的
        //线程 A 读到 10(arr[current])
        //线程 B 读到 10(arr[current])
        //线程 A 写回 11(arr[current])
        //线程 B 写回 11(arr[current])
    }

    /**
     * 计算近窗口总热度（各分段求和）。
     * @param key 缓存键
     * @return 热度值
     */
    public int heat(String key) {
        int[] arr = counters.get(key);
        if (arr == null) {
            return 0;
        }

        int sum = 0;
        for (int v : arr) {
            sum += v;
        }
        return sum;
    }

    /**
     * 计算热度评级：根据总热度与阈值映射到等级。
     * 阈值来源：properties.hotkey.levelLow/Medium/High。
     * @param key 缓存键
     * @return 热度等级
     */
    public Level level(String key) {
        int h = heat(key);
        if (h >= properties.getHotkey().getLevelHigh()) {
            return Level.HIGH;
        }
        if (h >= properties.getHotkey().getLevelMedium()) {
            return Level.MEDIUM;
        }
        if (h >= properties.getHotkey().getLevelLow()) {
            return Level.LOW;
        }

        return Level.NONE;
    }

    /**
     * 计算公共页面的动态 TTL：基准 TTL + 等级扩展秒数。
     * @param baseTtlSeconds 基准 TTL 秒数
     * @param key 缓存键
     * @return 动态 TTL 秒数
     */
    public int ttlForPublic(int baseTtlSeconds, String key) {
        Level l = level(key);
        return baseTtlSeconds + extendSeconds(l);
    }

    /**
     * 计算“我的发布”页面的动态 TTL：基准 TTL + 等级扩展秒数。
     * @param baseTtlSeconds 基准 TTL 秒数
     * @param key 缓存键
     * @return 动态 TTL 秒数
     */
    public int ttlForMine(int baseTtlSeconds, String key) {
        Level l = level(key);
        return baseTtlSeconds + extendSeconds(l);
    }

    /**
     * 根据热度等级返回扩展秒数。
     * @param l 热度等级
     * @return 扩展秒数
     */
    private int extendSeconds(Level l) {
        return switch (l) {
            case HIGH -> properties.getHotkey().getExtendHighSeconds();
            case MEDIUM -> properties.getHotkey().getExtendMediumSeconds();
            case LOW -> properties.getHotkey().getExtendLowSeconds();
            default -> 0;
        };
    }

    /**
     * 定时轮转当前分段，清零新分段以实现滑动窗口统计。
     * 触发频率由配置 `cache.hotkey.segment-seconds` 指定（单位秒）。
     */
    @Scheduled(fixedRateString = "${cache.hotkey.segment-seconds:10}000")
    //Spring 创建 Bean 的时候，它会顺手检查这个 Bean 里面有没有 @Scheduled 方法。
    //它会把这个方法包装成一个任务，然后交给调度器。底层真正负责“每隔几秒执行一次”的，一般是 Java 自带的：ScheduledExecutorService
    //ScheduledExecutorService 就是 Java 官方提供的定时任务线程池，用来实现延迟执行、固定频率执行、固定延迟执行这些功能。
    //如果你没有额外配置，Spring 的默认定时任务调度器通常是单线程。所有 @Scheduled 方法默认排队执行
    public void rotate() {
        //下面这两个get和set之间有空隙，会有并发安全问题，但是这个方法10s才调用一次，并且是单线程的，基本是安全的
        //但是get和set分别都是线程安全的，因为1. volatile 保证可见性 2. 底层硬件/ JVM 保证单次读写是原子的
        int next = (current.get() + 1) % segments;
        current.set(next);
        for (int[] arr : counters.values()) {
            arr[next] = 0;
        }
        //这个代码的顺序也有可能出现线程安全问题，可以通过调整代码顺序避免，根本原因还是int[]不是线程安全的

        //rotate 把 current 改成 next
        //请求线程读取到新的 current
        //请求线程 arr[next]++
        //rotate 又把 arr[next] = 0
    }

    /**
     * 重置指定 key 的滑窗计数（全部清零）。
     * 用于手动降级或在配置变更后清理历史热度。
     * @param key 缓存键
     */
    public void reset(String key) {
        int[] arr = counters.get(key);
        if (arr != null) Arrays.fill(arr, 0);
    }
}
