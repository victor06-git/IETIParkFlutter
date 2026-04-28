package com.mdominguez.ietiParkAndroid;

public final class AndroidHardwareInputBridge {
    private static boolean captureEnabled = false;
    private static boolean leftPressed = false;
    private static boolean rightPressed = false;
    private static boolean upPressed = false;
    private static boolean downPressed = false;
    private static boolean jumpHeld = false;
    private static boolean jumpQueued = false;
    private static boolean resetQueued = false;

    private AndroidHardwareInputBridge() {
    }

    public static synchronized void setCaptureEnabled(boolean enabled) {
        captureEnabled = enabled;
        if (!enabled) {
            reset();
        }
    }

    public static synchronized boolean isCaptureEnabled() {
        return captureEnabled;
    }

    public static synchronized void resetState() {
        reset();
    }

    public static synchronized void pressLeft() {
        leftPressed = true;
    }

    public static synchronized void releaseLeft() {
        leftPressed = false;
    }

    public static synchronized void pressRight() {
        rightPressed = true;
    }

    public static synchronized void releaseRight() {
        rightPressed = false;
    }

    public static synchronized void pressUp() {
        upPressed = true;
    }

    public static synchronized void releaseUp() {
        upPressed = false;
    }

    public static synchronized void pressDown() {
        downPressed = true;
    }

    public static synchronized void releaseDown() {
        downPressed = false;
    }

    public static synchronized void pressJump() {
        if (!jumpHeld) {
            jumpQueued = true;
        }
        jumpHeld = true;
    }

    public static synchronized void releaseJump() {
        jumpHeld = false;
    }

    public static synchronized void pressReset() {
        resetQueued = true;
    }

    public static synchronized boolean isLeftPressed() {
        return leftPressed;
    }

    public static synchronized boolean isRightPressed() {
        return rightPressed;
    }

    public static synchronized boolean isUpPressed() {
        return upPressed;
    }

    public static synchronized boolean isDownPressed() {
        return downPressed;
    }

    public static synchronized boolean isJumpHeld() {
        return jumpHeld;
    }

    public static synchronized boolean consumeJumpQueued() {
        boolean value = jumpQueued;
        jumpQueued = false;
        return value;
    }

    public static synchronized boolean consumeResetQueued() {
        boolean value = resetQueued;
        resetQueued = false;
        return value;
    }

    private static void reset() {
        leftPressed = false;
        rightPressed = false;
        upPressed = false;
        downPressed = false;
        jumpHeld = false;
        jumpQueued = false;
        resetQueued = false;
    }
}
