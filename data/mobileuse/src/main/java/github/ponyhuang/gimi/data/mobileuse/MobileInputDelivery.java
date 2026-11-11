package github.ponyhuang.gimi.data.mobileuse;

import java.util.function.BooleanSupplier;

/** 成对投递输入：只有按下明确未送达才允许命令兜底，部分成功不能重放完整动作。 */
final class MobileInputDelivery {
    private MobileInputDelivery() {}

    static boolean pair(BooleanSupplier down, BooleanSupplier up, BooleanSupplier fallback) {
        if (!down.getAsBoolean()) return fallback.getAsBoolean();
        // 抬起注入失败时重试一次（见 MobileInputDeliveryTest 的 "down","up","up" 契约）；
        // 只重试抬起，不重放完整动作——按下已送达的触摸不能二次触发。
        if (up.getAsBoolean() || up.getAsBoolean()) return true;
        // 按下已送达，不能返回“拒绝”让调用方误以为整次动作未执行。
        throw new IllegalStateException("Input down delivered but release unconfirmed; observe before continuing");
    }
}
