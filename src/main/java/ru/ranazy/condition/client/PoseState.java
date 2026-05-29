package ru.ranazy.condition.client;

public class PoseState {
    public final boolean crouching;
    public final boolean crawling;
    public final float aimingProgress;
    public final boolean reloading;
    public final boolean bolting;

    public final float pitch;
    public final float headYawOffset;
    public final long lastSendTime;
    public final long lastMoveTime;

    public PoseState(boolean crouching, boolean crawling, float aimingProgress, boolean reloading, boolean bolting, float pitch, float headYawOffset, long lastSendTime, long lastMoveTime) {
        this.crouching = crouching;
        this.crawling = crawling;
        this.aimingProgress = aimingProgress;
        this.reloading = reloading;
        this.bolting = bolting;
        this.pitch = pitch;
        this.headYawOffset = headYawOffset;
        this.lastSendTime = lastSendTime;
        this.lastMoveTime = lastMoveTime;
    }

    public boolean hasChanged(boolean currentCrouching, boolean currentCrawling, float currentAiming, boolean currentReloading, boolean currentBolting, float currentPitch, float currentHeadYawOffset, long currentLastMoveTime, long currentTime) {
        // Мгновенные (дискретные) смены состояния — отправляем без задержек
        if (this.crouching != currentCrouching ||
            this.crawling != currentCrawling ||
            this.reloading != currentReloading ||
            this.bolting != currentBolting) {
            return true;
        }

        // Жесткое ограничение в 50мс (20 TPS) для защиты от спама пакетами
        if (currentTime - this.lastSendTime < 50) {
            return false;
        }

        // Если игрок двигается (или двигался в последние 500мс), всегда обновляем для идеальной плавности
        if ((currentTime - currentLastMoveTime) < 500) {
            return true;
        }

        // Если игрок стоит, реагируем на любые микро-движения мыши (идеальная точность: 0.1 градуса)
        return Math.abs(this.aimingProgress - currentAiming) > 0.01f ||
               Math.abs(this.pitch - currentPitch) > 0.1f ||
               Math.abs(this.headYawOffset - currentHeadYawOffset) > 0.1f;
    }
}
