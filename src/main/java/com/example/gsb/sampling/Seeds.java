package com.example.gsb.sampling;

/**
 * 随机种子工具：用 SplitMix64 finalizer 对外部种子做一次确定性混洗。
 *
 * <p>原因：{@link java.util.Random} 内部是线性同余发生器，两个数值相邻的种子
 * （如 100、101、102、…）产生的前几个随机数高度相关。做多次独立重复试验时
 * 常用“基础种子 + 试验序号”，混洗后可保证这些试验统计独立；同时相同种子
 * 仍确定性映射到同一内部种子，可复现性不变。
 */
final class Seeds {

    private Seeds() {
    }

    static long mix64(long seed) {
        long z = seed;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
