
package android.telecom;

/**
 * @hide
 */
public class MultiPartCallHelper {
    /**
     * MPC (Multi-Part-Call) mode: hang up background call and accept ringing/waiting call.
     *
     * @hide
     */
    public static final int MPC_MODE_HB = 0;

    /**
     * MPC (Multi-Part-Call) mode: hang up foreground call and accept ringing/waiting call.
     *
     * @hide
     */
    public static final int MPC_MODE_HF = 1;

    /**
     * MPC (Multi-Part-Call) mode: hang up background & foreground call and accept ringing/waiting call.
     *
     * @hide
     */
    public static final int MPC_MODE_HBF = 2;
}
