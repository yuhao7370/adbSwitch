package com.elleng.adbswitch;

/** Synchronous operation; the caller owns serialization with restore and debug writes. */
final class QuickLaunchTransaction {
    interface Operations {
        DebugController.State readDebug();
        DebugController.State closeDebug() throws Exception;
        void remember(DebugController.State before);
        void prepareRear() throws Exception;
        void launch() throws Exception;
        void restoreRear() throws Exception;
        DebugController.State restoreDebug(DebugController.State before) throws Exception;
    }

    static final class Result {
        final DebugController.State after;
        final String error;
        Result(DebugController.State after, String error) { this.after = after; this.error = error; }
    }

    static Result run(Operations operations) {
        DebugController.State before = operations.readDebug();
        if (before == null) return new Result(null, "无法确认调试状态，请检查授权");
        try {
            DebugController.State after = operations.closeDebug();
            if (after == null || !after.allOff()) throw new IllegalStateException("调试关闭未生效，已取消启动");
            if (before.anyOn()) operations.remember(before);
            operations.prepareRear();
            // Launch is part of the operation, not a deferred UI callback. A queued restore
            // must not run between display preparation and starting the target activity.
            operations.launch();
            return new Result(after, null);
        } catch (Exception failure) {
            String error = message(failure);
            try { operations.restoreRear(); }
            catch (Exception restore) { error += "；背屏恢复待重试：" + message(restore); }
            // Only a new readback can confirm rollback; the pre-write snapshot is not proof.
            DebugController.State after = null;
            try { after = operations.restoreDebug(before); }
            catch (Exception restore) { error += "；" + message(restore); }
            if (after == null || before.usb != after.usb || before.wireless != after.wireless) {
                error += "；调试状态恢复未确认，请使用主开关重试";
            }
            return new Result(after, error);
        }
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
