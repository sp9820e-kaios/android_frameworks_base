/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package android.hardware;

import android.os.Build;
import android.annotation.SystemApi;

/**
 * Class representing a sensor. Use {@link SensorManager#getSensorList} to get
 * the list of available Sensors.
 *
 * @see SensorManager
 * @see SensorEventListener
 * @see SensorEvent
 *
 */
public final class Sensor {

    /**
     * A constant describing an accelerometer sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_ACCELEROMETER = 1;

    /**
     * A constant string describing an accelerometer sensor type.
     *
     * @see #TYPE_ACCELEROMETER
     */
    public static final String STRING_TYPE_ACCELEROMETER = "android.sensor.accelerometer";

    /**
     * A constant describing a magnetic field sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_MAGNETIC_FIELD = 2;

    /**
     * A constant string describing a magnetic field sensor type.
     *
     * @see #TYPE_MAGNETIC_FIELD
     */
    public static final String STRING_TYPE_MAGNETIC_FIELD = "android.sensor.magnetic_field";

    /**
     * A constant describing an orientation sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     *
     * @deprecated use {@link android.hardware.SensorManager#getOrientation
     *             SensorManager.getOrientation()} instead.
     */
    @Deprecated
    public static final int TYPE_ORIENTATION = 3;

    /**
     * A constant string describing an orientation sensor type.
     *
     * @see #TYPE_ORIENTATION
     * @deprecated use {@link android.hardware.SensorManager#getOrientation
     *             SensorManager.getOrientation()} instead.
     */
    @Deprecated
    public static final String STRING_TYPE_ORIENTATION = "android.sensor.orientation";

    /**
     * A constant describing a gyroscope sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details. */
    public static final int TYPE_GYROSCOPE = 4;

    /**
     * A constant string describing a gyroscope sensor type.
     *
     * @see #TYPE_GYROSCOPE
     */
    public static final String STRING_TYPE_GYROSCOPE = "android.sensor.gyroscope";

    /**
     * A constant describing a light sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_LIGHT = 5;

    /**
     * A constant string describing a light sensor type.
     *
     * @see #TYPE_LIGHT
     */
    public static final String STRING_TYPE_LIGHT = "android.sensor.light";

    /**
     * A constant describing a pressure sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_PRESSURE = 6;

    /**
     * A constant string describing a pressure sensor type.
     *
     * @see #TYPE_PRESSURE
     */
    public static final String STRING_TYPE_PRESSURE = "android.sensor.pressure";

    /**
     * A constant describing a temperature sensor type
     *
     * @deprecated use
     *             {@link android.hardware.Sensor#TYPE_AMBIENT_TEMPERATURE
     *             Sensor.TYPE_AMBIENT_TEMPERATURE} instead.
     */
    @Deprecated
    public static final int TYPE_TEMPERATURE = 7;

    /**
     * A constant string describing a temperature sensor type
     *
     * @see #TYPE_TEMPERATURE
     * @deprecated use
     *             {@link android.hardware.Sensor#STRING_TYPE_AMBIENT_TEMPERATURE
     *             Sensor.STRING_TYPE_AMBIENT_TEMPERATURE} instead.
     */
    @Deprecated
    public static final String STRING_TYPE_TEMPERATURE = "android.sensor.temperature";

    /**
     * A constant describing a proximity sensor type. This is a wake up sensor.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     * @see #isWakeUpSensor()
     */
    public static final int TYPE_PROXIMITY = 8;

    /**
     * A constant string describing a proximity sensor type.
     *
     * @see #TYPE_PROXIMITY
     */
    public static final String STRING_TYPE_PROXIMITY = "android.sensor.proximity";

    /**
     * A constant describing a gravity sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_GRAVITY = 9;

    /**
     * A constant string describing a gravity sensor type.
     *
     * @see #TYPE_GRAVITY
     */
    public static final String STRING_TYPE_GRAVITY = "android.sensor.gravity";

    /**
     * A constant describing a linear acceleration sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_LINEAR_ACCELERATION = 10;

    /**
     * A constant string describing a linear acceleration sensor type.
     *
     * @see #TYPE_LINEAR_ACCELERATION
     */
    public static final String STRING_TYPE_LINEAR_ACCELERATION =
        "android.sensor.linear_acceleration";

    /**
     * A constant describing a rotation vector sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_ROTATION_VECTOR = 11;

    /**
     * A constant string describing a rotation vector sensor type.
     *
     * @see #TYPE_ROTATION_VECTOR
     */
    public static final String STRING_TYPE_ROTATION_VECTOR = "android.sensor.rotation_vector";

    /**
     * A constant describing a relative humidity sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_RELATIVE_HUMIDITY = 12;

    /**
     * A constant string describing a relative humidity sensor type
     *
     * @see #TYPE_RELATIVE_HUMIDITY
     */
    public static final String STRING_TYPE_RELATIVE_HUMIDITY = "android.sensor.relative_humidity";

    /**
     * A constant describing an ambient temperature sensor type.
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values}
     * for more details.
     */
    public static final int TYPE_AMBIENT_TEMPERATURE = 13;

    /**
     * A constant string describing an ambient temperature sensor type.
     *
     * @see #TYPE_AMBIENT_TEMPERATURE
     */
    public static final String STRING_TYPE_AMBIENT_TEMPERATURE =
        "android.sensor.ambient_temperature";

    /**
     * A constant describing an uncalibrated magnetic field sensor type.
     * <p>
     * Similar to {@link #TYPE_MAGNETIC_FIELD} but the hard iron calibration (device calibration
     * due to distortions that arise from magnetized iron, steel or permanent magnets on the
     * device) is not considered in the given sensor values. However, such hard iron bias values
     * are returned to you separately in the result {@link android.hardware.SensorEvent#values}
     * so you may use them for custom calibrations.
     * <p>Also, no periodic calibration is performed
     * (i.e. there are no discontinuities in the data stream while using this sensor) and
     * assumptions that the magnetic field is due to the Earth's poles is avoided, but
     * factory calibration and temperature compensation have been performed.
     * </p>
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values} for more
     * details.
     */
    public static final int TYPE_MAGNETIC_FIELD_UNCALIBRATED = 14;
    /**
     * A constant string describing an uncalibrated magnetic field sensor type.
     *
     * @see #TYPE_MAGNETIC_FIELD_UNCALIBRATED
     */
    public static final String STRING_TYPE_MAGNETIC_FIELD_UNCALIBRATED =
        "android.sensor.magnetic_field_uncalibrated";

    /**
     * A constant describing an uncalibrated rotation vector sensor type.
     * <p>Identical to {@link #TYPE_ROTATION_VECTOR} except that it doesn't
     * use the geomagnetic field. Therefore the Y axis doesn't
     * point north, but instead to some other reference, that reference is
     * allowed to drift by the same order of magnitude as the gyroscope
     * drift around the Z axis.
     * <p>
     * In the ideal case, a phone rotated and returning to the same real-world
     * orientation should report the same game rotation vector
     * (without using the earth's geomagnetic field). However, the orientation
     * may drift somewhat over time.
     * </p>
     * <p>See {@link android.hardware.SensorEvent#values SensorEvent.values} for more
     * details.
     */
    public static final int TYPE_GAME_ROTATION_VECTOR = 15;

    /**
     * A constant string describing an uncalibrated rotation vector sensor type.
     *
     * @see #TYPE_GAME_ROTATION_VECTOR
     */
    public static final String STRING_TYPE_GAME_ROTATION_VECTOR =
        "android.sensor.game_rotation_vector";

    /**
     * A constant describing an uncalibrated gyroscope sensor type.
     * <p>Similar to {@link #TYPE_GYROSCOPE} but no gyro-drift compensation has been performed
     * to adjust the given sensor values. However, such gyro-drift bias values
     * are returned to you separately in the result {@link android.hardware.SensorEvent#values}
     * so you may use them for custom calibrations.
     * <p>Factory calibration and temperature compensation is still applied
     * to the rate of rotation (angular speeds).
     * </p>
     * <p> See {@link android.hardware.SensorEvent#values SensorEvent.values} for more
     * details.
     */
    public static final int TYPE_GYROSCOPE_UNCALIBRATED = 16;

    /**
     * A constant string describing an uncalibrated gyroscope sensor type.
     *
     * @see #TYPE_GYROSCOPE_UNCALIBRATED
     */
    public static final String STRING_TYPE_GYROSCOPE_UNCALIBRATED =
        "android.sensor.gyroscope_uncalibrated";

    /**
     * A constant describing a significant motion trigger sensor.
     * <p>
     * It triggers when an event occurs and then automatically disables
     * itself. The sensor continues to operate while the device is asleep
     * and will automatically wake the device to notify when significant
     * motion is detected. The application does not need to hold any wake
     * locks for this sensor to trigger. This is a wake up sensor.
     * <p>See {@link TriggerEvent} for more details.
     *
     * @see #isWakeUpSensor()
     */
    public static final int TYPE_SIGNIFICANT_MOTION = 17;

    /**
     * A constant string describing a significant motion trigger sensor.
     *
     * @see #TYPE_SIGNIFICANT_MOTION
     */
    public static final String STRING_TYPE_SIGNIFICANT_MOTION =
        "android.sensor.significant_motion";

    /**
     * A constant describing a step detector sensor.
     * <p>
     * A sensor of this type triggers an event each time a step is taken by the user. The only
     * allowed value to return is 1.0 and an event is generated for each step. Like with any other
     * event, the timestamp indicates when the event (here the step) occurred, this corresponds to
     * when the foot hit the ground, generating a high variation in acceleration. This sensor is
     * only for detecting every individual step as soon as it is taken, for example to perform dead
     * reckoning. If you only need aggregate number of steps taken over a period of time, register
     * for {@link #TYPE_STEP_COUNTER} instead. It is defined as a
     * {@link Sensor#REPORTING_MODE_SPECIAL_TRIGGER} sensor.
     * <p>
     * See {@link android.hardware.SensorEvent#values SensorEvent.values} for more details.
     */
    public static final int TYPE_STEP_DETECTOR = 18;

    /**
     * A constant string describing a step detector sensor.
     *
     * @see #TYPE_STEP_DETECTOR
     */
    public static final String STRING_TYPE_STEP_DETECTOR = "android.sensor.step_detector";

    /**
     * A constant describing a step counter sensor.
     * <p>
     * A sensor of this type returns the number of steps taken by the user since the last reboot
     * while activated. The value is returned as a float (with the fractional part set to zero) and
     * is reset to zero only on a system reboot. The timestamp of the event is set to the time when
     * the last step for that event was taken. This sensor is implemented in hardware and is
     * expected to be low power. If you want to continuously track the number of steps over a long
     * period of time, do NOT unregister for this sensor, so that it keeps counting steps in the
     * background even when the AP is in suspend mode and report the aggregate count when the AP
     * is awake. Application needs to stay registered for this sensor because step counter does not
     * count steps if it is not activated. This sensor is ideal for fitness tracking applications.
     * It is defined as an {@link Sensor#REPORTING_MODE_ON_CHANGE} sensor.
     * <p>
     * See {@link android.hardware.SensorEvent#values SensorEvent.values} for more details.
     */
    public static final int TYPE_STEP_COUNTER = 19;

    /**
     * A constant string describing a step counter sensor.
     *
     * @see #TYPE_STEP_COUNTER
     */
    public static final String STRING_TYPE_STEP_COUNTER = "android.sensor.step_counter";

    /**
     * A constant describing a geo-magnetic rotation vector.
     * <p>
     * Similar to {@link #TYPE_ROTATION_VECTOR}, but using a magnetometer instead of using a
     * gyroscope. This sensor uses lower power than the other rotation vectors, because it doesn't
     * use the gyroscope. However, it is more noisy and will work best outdoors.
     * <p>
     * See {@link android.hardware.SensorEvent#values SensorEvent.values} for more details.
     */
    public static final int TYPE_GEOMAGNETIC_ROTATION_VECTOR = 20;

    /**
     * A constant string describing a geo-magnetic rotation vector.
     *
     * @see #TYPE_GEOMAGNETIC_ROTATION_VECTOR
     */
    public static final String STRING_TYPE_GEOMAGNETIC_ROTATION_VECTOR =
        "android.sensor.geomagnetic_rotation_vector";

    /**
     * A constant describing a heart rate monitor.
     * <p>
     * The reported value is the heart rate in beats per minute.
     * <p>
     * The reported accuracy represents the status of the monitor during the reading. See the
     * {@code SENSOR_STATUS_*} constants in {@link android.hardware.SensorManager SensorManager}
     * for more details on accuracy/status values. In particular, when the accuracy is
     * {@code SENSOR_STATUS_UNRELIABLE} or {@code SENSOR_STATUS_NO_CONTACT}, the heart rate
     * value should be discarded.
     * <p>
     * This sensor requires permission {@code android.permission.BODY_SENSORS}.
     * It will not be returned by {@code SensorManager.getSensorsList} nor
     * {@code SensorManager.getDefaultSensor} if the application doesn't have this permission.
     */
    public static final int TYPE_HEART_RATE = 21;

    /**
     * A constant string describing a heart rate monitor.
     *
     * @see #TYPE_HEART_RATE
     */
    public static final String STRING_TYPE_HEART_RATE = "android.sensor.heart_rate";

    /**
     * A sensor of this type generates an event each time a tilt event is detected. A tilt event
     * is generated if the direction of the 2-seconds window average gravity changed by at
     * least 35 degrees since the activation of the sensor. It is a wake up sensor.
     *
     * @hide
     * @see #isWakeUpSensor()
     */
    public static final int TYPE_TILT_DETECTOR = 22;

    /**
     * A constant string describing a wake up tilt detector sensor type.
     *
     * @hide
     * @see #TYPE_WAKE_UP_TILT_DETECTOR
     */
    public static final String SENSOR_STRING_TYPE_TILT_DETECTOR =
            "android.sensor.tilt_detector";

    /**
     * A constant describing a wake gesture sensor.
     * <p>
     * Wake gesture sensors enable waking up the device based on a device specific motion.
     * <p>
     * When this sensor triggers, the device behaves as if the power button was pressed, turning the
     * screen on. This behavior (turning on the screen when this sensor triggers) might be
     * deactivated by the user in the device settings. Changes in settings do not impact the
     * behavior of the sensor: only whether the framework turns the screen on when it triggers.
     * <p>
     * The actual gesture to be detected is not specified, and can be chosen by the manufacturer of
     * the device. This sensor must be low power, as it is likely to be activated 24/7.
     * Values of events created by this sensors should not be used.
     *
     * @see #isWakeUpSensor()
     * @hide This sensor is expected to only be used by the system ui
     */
    public static final int TYPE_WAKE_GESTURE = 23;

    /**
     * A constant string describing a wake gesture sensor.
     *
     * @hide This sensor is expected to only be used by the system ui
     * @see #TYPE_WAKE_GESTURE
     */
    public static final String STRING_TYPE_WAKE_GESTURE = "android.sensor.wake_gesture";

    /**
     * A constant describing a wake gesture sensor.
     * <p>
     * A sensor enabling briefly turning the screen on to enable the user to
     * glance content on screen based on a specific motion.  The device should
     * turn the screen off after a few moments.
     * <p>
     * When this sensor triggers, the device turns the screen on momentarily
     * to allow the user to glance notifications or other content while the
     * device remains locked in a non-interactive state (dozing). This behavior
     * (briefly turning on the screen when this sensor triggers) might be deactivated
     * by the user in the device settings. Changes in settings do not impact the
     * behavior of the sensor: only whether the framework briefly turns the screen on
     * when it triggers.
     * <p>
     * The actual gesture to be detected is not specified, and can be chosen by the manufacturer of
     * the device. This sensor must be low power, as it is likely to be activated 24/7.
     * Values of events created by this sensors should not be used.
     *
     * @see #isWakeUpSensor()
     * @hide This sensor is expected to only be used by the system ui
     */
    public static final int TYPE_GLANCE_GESTURE = 24;

    /**
     * A constant string describing a wake gesture sensor.
     *
     * @hide This sensor is expected to only be used by the system ui
     * @see #TYPE_GLANCE_GESTURE
     */
    public static final String STRING_TYPE_GLANCE_GESTURE = "android.sensor.glance_gesture";

     /**
     * A constant describing a pick up sensor.
     *
     * A sensor of this type triggers when the device is picked up regardless of wherever it was
     * before (desk, pocket, bag). The only allowed return value is 1.0. This sensor deactivates
     * itself immediately after it triggers.
     *
     * @hide Expected to be used internally for always on display.
     */
    public static final int TYPE_PICK_UP_GESTURE = 25;

    /**
     * A constant string describing a pick up sensor.
     *
     * @hide This sensor is expected to be used internally for always on display.
     * @see #TYPE_PICK_UP_GESTURE
     */
    public static final String STRING_TYPE_PICK_UP_GESTURE = "android.sensor.pick_up_gesture";

     /**
     * A constant describing a wrist tilt gesture sensor.
     *
     * A sensor of this type triggers when the device face is tilted towards the user.
     * The only allowed return value is 1.0.
     * This sensor remains active until disabled.
     *
     * @hide This sensor is expected to only be used by the system ui
     */
    @SystemApi
    public static final int TYPE_WRIST_TILT_GESTURE = 26;

    /**
     * A constant string describing a wrist tilt gesture sensor.
     *
     * @hide This sensor is expected to only be used by the system ui
     * @see #TYPE_WRIST_TILT_GESTURE
     */
    @SystemApi
    public static final String STRING_TYPE_WRIST_TILT_GESTURE = "android.sensor.wrist_tilt_gesture";

    /* SPRD: add 4 new sensor type @ { */

    private static final int SPRD_SENSOR_TYPE_BASE = 0x10000;

    /**
     * A constant describing a shake sensor.
     *
     * A sensor of this type triggers when the device is shaken.
     * This sensor deactivates itself immediately after it triggers.
     *
     * @hide This sensor is expected to only be used by the System Application.
     */
    public static final int TYPE_SPRDHUB_SHAKE = SPRD_SENSOR_TYPE_BASE + 1;

    /**
     * A constant string describing a shake sensor.
     *
     * @hide
     */
    public static final String STRING_TYPE_SPRDHUB_SHAKE = "sprd.sensorhub.shake";

    /**
     * A constant describing a pocket-mode sensor.
     *
     * A sensor of this type triggers when the device is put into a pocket.
     * The only allowed return value is 1.0. This sensor deactivates
     * itself immediately after it triggers.
     *
     * @hide This sensor is expected to only be used by the System Application.
     */
    public static final int TYPE_SPRDHUB_POCKET_MODE = SPRD_SENSOR_TYPE_BASE + 2;

    /**
     * A constant string describing a pocket-mode sensor.
     *
     * @hide
     */
    public static final String STRING_TYPE_SPRDHUB_POCKET_MODE = "sprd.sensorhub.pocket_mode";

    /**
     * A constant describing a tap sensor.
     *
     * A sensor of this type triggers when the device is tapped single or twice.
     * This sensor deactivates itself immediately after it triggers.
     *
     * @hide Expected to be used by system core service.
     */
    public static final int TYPE_SPRDHUB_TAP = SPRD_SENSOR_TYPE_BASE + 3;

    /**
     * A constant string describing a tap sensor.
     *
     * @hide
     */
    public static final String STRING_TYPE_SPRDHUB_TAP = "sprd.sensorhub.tap";

    /**
     * A constant describing a face-up/face-down sensor.
     *
     * A sensor of this type triggers when the device is faced up or faced down.
     * This sensor deactivates itself immediately after it triggers.
     *
     * @hide Expected to be used by System Application.
     */
    public static final int TYPE_SPRDHUB_FACE_UP_DOWN = SPRD_SENSOR_TYPE_BASE + 4;

    /**
     * A constant string describing a face-up/face-down sensor.
     *
     * @hide
     */
    public static final String STRING_TYPE_SPRDHUB_FACE_UP_DOWN = "sprd.sensorhub.face_up_down";

    /**
     * A constant describing a flip sensor.
     *
     * A sensor of this type triggers when the device is fliped.
     * reporting-mode: on_change.
     *
     * @hide Expected to be used by System Application.
     */
    public static final int TYPE_SPRDHUB_FLIP = SPRD_SENSOR_TYPE_BASE + 10;

    /**
     * A constant string describing a flip sensor.
     *
     * @hide
     */
    public static final String STRING_TYPE_SPRDHUB_FLIP = "sprd.sensorhub.flip";

    /**
     * A constant describing a hand-up sensor.
     *
     * A sensor of this type triggers when the device is handed up
     * reporting mode: on_change
     *
     * @hide Expected to be used by System Application.
     */
    public static final int TYPE_SPRDHUB_HAND_UP = SPRD_SENSOR_TYPE_BASE + 12;

    /**
     * A constant string describing a hand-up sensor.
     *
     * @hide
     */
    public static final String STRING_TYPE_SPRDHUB_HAND_UP = "sprd.sensorhub.hand_up";

    /**
     * A constant describing a hand-down sensor.
     *
     * A sensor of this type triggers when the device is handed down
     * reporting mode: on_change
     *
     * @hide Expected to be used by System Application.
     */
    public static final int TYPE_SPRDHUB_HAND_DOWN = SPRD_SENSOR_TYPE_BASE + 13;

    /**
     * A constant string describing a hand-down sensor.
     *
     * @hide
     */
    public static final String STRING_TYPE_SPRDHUB_HAND_DOWN = "sprd.sensorhub.hand_down";

    /* @ } */

    /**
     * A constant describing all sensor types.
     */
    public static final int TYPE_ALL = -1;

    // If this flag is set, the sensor defined as a wake up sensor. This field and REPORTING_MODE_*
    // constants are defined as flags in sensors.h. Modify at both places if needed.
    private static final int SENSOR_FLAG_WAKE_UP_SENSOR = 1;

    /**
     * Events are reported at a constant rate which is set by the rate parameter of
     * {@link SensorManager#registerListener(SensorEventListener, Sensor, int)}. Note: If other
     * applications are requesting a higher rate, the sensor data might be delivered at faster rates
     * than requested.
     */
    public static final int REPORTING_MODE_CONTINUOUS = 0;

    /**
     * Events are reported only when the value changes. Event delivery rate can be limited by
     * setting appropriate value for rate parameter of
     * {@link SensorManager#registerListener(SensorEventListener, Sensor, int)} Note: If other
     * applications are requesting a higher rate, the sensor data might be delivered at faster rates
     * than requested.
     */
    public static final int REPORTING_MODE_ON_CHANGE = 1;

    /**
     * Events are reported in one-shot mode. Upon detection of an event, the sensor deactivates
     * itself and then sends a single event. Sensors of this reporting mode must be registered to
     * using {@link SensorManager#requestTriggerSensor(TriggerEventListener, Sensor)}.
     */
    public static final int REPORTING_MODE_ONE_SHOT = 2;

    /**
     * Events are reported as described in the description of the sensor. The rate passed to
     * registerListener might not have an impact on the rate of event delivery. See the sensor
     * definition for more information on when and how frequently the events are reported. For
     * example, step detectors report events when a step is detected.
     *
     * @see SensorManager#registerListener(SensorEventListener, Sensor, int, int)
     */
    public static final int REPORTING_MODE_SPECIAL_TRIGGER = 3;

    // Mask for the LSB 2nd, 3rd and fourth bits.
    private static final int REPORTING_MODE_MASK = 0xE;
    private static final int REPORTING_MODE_SHIFT = 1;

    // MASK for LSB fifth bit. Used to know whether the sensor supports data injection or not.
    private static final int DATA_INJECTION_MASK = 0x10;
    private static final int DATA_INJECTION_SHIFT = 4;

    // TODO(): The following arrays are fragile and error-prone. This needs to be refactored.

    // Note: This needs to be updated, whenever a new sensor is added.
    // Holds the reporting mode and maximum length of the values array
    // associated with
    // {@link SensorEvent} or {@link TriggerEvent} for the Sensor
    private static final int[] sSensorReportingModes = {
            0, // padding because sensor types start at 1
            3, // SENSOR_TYPE_ACCELEROMETER
            3, // SENSOR_TYPE_GEOMAGNETIC_FIELD
            3, // SENSOR_TYPE_ORIENTATION
            3, // SENSOR_TYPE_GYROSCOPE
            3, // SENSOR_TYPE_LIGHT
            3, // SENSOR_TYPE_PRESSURE
            3, // SENSOR_TYPE_TEMPERATURE
            3, // SENSOR_TYPE_PROXIMITY
            3, // SENSOR_TYPE_GRAVITY
            3, // SENSOR_TYPE_LINEAR_ACCELERATION
            5, // SENSOR_TYPE_ROTATION_VECTOR
            3, // SENSOR_TYPE_RELATIVE_HUMIDITY
            3, // SENSOR_TYPE_AMBIENT_TEMPERATURE
            6, // SENSOR_TYPE_MAGNETIC_FIELD_UNCALIBRATED
            4, // SENSOR_TYPE_GAME_ROTATION_VECTOR
            6, // SENSOR_TYPE_GYROSCOPE_UNCALIBRATED
            1, // SENSOR_TYPE_SIGNIFICANT_MOTION
            1, // SENSOR_TYPE_STEP_DETECTOR
            1, // SENSOR_TYPE_STEP_COUNTER
            5, // SENSOR_TYPE_GEOMAGNETIC_ROTATION_VECTOR
            1, // SENSOR_TYPE_HEART_RATE_MONITOR
            1, // SENSOR_TYPE_WAKE_UP_TILT_DETECTOR
            1, // SENSOR_TYPE_WAKE_GESTURE
            1, // SENSOR_TYPE_GLANCE_GESTURE
            1, // SENSOR_TYPE_PICK_UP_GESTURE
            1, // SENSOR_TYPE_WRIST_TILT_GESTURE
    };

    /**
     * Each sensor has exactly one reporting mode associated with it. This method returns the
     * reporting mode constant for this sensor type.
     *
     * @return Reporting mode for the input sensor, one of REPORTING_MODE_* constants.
     * @see #REPORTING_MODE_CONTINUOUS
     * @see #REPORTING_MODE_ON_CHANGE
     * @see #REPORTING_MODE_ONE_SHOT
     * @see #REPORTING_MODE_SPECIAL_TRIGGER
     */
    public int getReportingMode() {
        return ((mFlags & REPORTING_MODE_MASK) >> REPORTING_MODE_SHIFT);
    }

    static int getMaxLengthValuesArray(Sensor sensor, int sdkLevel) {
        // RotationVector length has changed to 3 to 5 for API level 18
        // Set it to 3 for backward compatibility.
        if (sensor.mType == Sensor.TYPE_ROTATION_VECTOR &&
                sdkLevel <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return 3;
        }
        int offset = sensor.mType;
        if (offset >= sSensorReportingModes.length) {
            // we don't know about this sensor, so this is probably a
            // vendor-defined sensor, in that case, we don't know how many value
            // it has
            // so we return the maximum and assume the app will know.
            // FIXME: sensor HAL should advertise how much data is returned per
            // sensor
            return 16;
        }
        return sSensorReportingModes[offset];
    }

    /* Some of these fields are set only by the native bindings in
     * SensorManager.
     */
    private String  mName;
    private String  mVendor;
    private int     mVersion;
    private int     mHandle;
    private int     mType;
    private float   mMaxRange;
    private float   mResolution;
    private float   mPower;
    private int     mMinDelay;
    private int     mFifoReservedEventCount;
    private int     mFifoMaxEventCount;
    private String  mStringType;
    private String  mRequiredPermission;
    private int     mMaxDelay;
    private int     mFlags;

    Sensor() {
    }

    /**
     * @return name string of the sensor.
     */
    public String getName() {
        return mName;
    }

    /**
     * @return vendor string of this sensor.
     */
    public String getVendor() {
        return mVendor;
    }

    /**
     * @return generic type of this sensor.
     */
    public int getType() {
        return mType;
    }

    /**
     * @return version of the sensor's module.
     */
    public int getVersion() {
        return mVersion;
    }

    /**
     * @return maximum range of the sensor in the sensor's unit.
     */
    public float getMaximumRange() {
        return mMaxRange;
    }

    /**
     * @return resolution of the sensor in the sensor's unit.
     */
    public float getResolution() {
        return mResolution;
    }

    /**
     * @return the power in mA used by this sensor while in use
     */
    public float getPower() {
        return mPower;
    }

    /**
     * @return the minimum delay allowed between two events in microsecond
     * or zero if this sensor only returns a value when the data it's measuring
     * changes.
     */
    public int getMinDelay() {
        return mMinDelay;
    }

    /**
     * @return Number of events reserved for this sensor in the batch mode FIFO. This gives a
     * guarantee on the minimum number of events that can be batched.
     */
    public int getFifoReservedEventCount() {
        return mFifoReservedEventCount;
    }

    /**
     * @return Maximum number of events of this sensor that could be batched. If this value is zero
     * it indicates that batch mode is not supported for this sensor. If other applications
     * registered to batched sensors, the actual number of events that can be batched might be
     * smaller because the hardware FiFo will be partially used to batch the other sensors.
     */
    public int getFifoMaxEventCount() {
        return mFifoMaxEventCount;
    }

    /**
     * @return The type of this sensor as a string.
     */
    public String getStringType() {
        return mStringType;
    }

    /**
     * @hide
     * @return The permission required to access this sensor. If empty, no permission is required.
     */
    public String getRequiredPermission() {
        return mRequiredPermission;
    }

    /** @hide */
    public int getHandle() {
        return mHandle;
    }

    /**
     * This value is defined only for continuous and on-change sensors. It is the delay between two
     * sensor events corresponding to the lowest frequency that this sensor supports. When lower
     * frequencies are requested through registerListener() the events will be generated at this
     * frequency instead. It can be used to estimate when the batch FIFO may be full. Older devices
     * may set this value to zero. Ignore this value in case it is negative or zero.
     *
     * @return The max delay for this sensor in microseconds.
     */
    public int getMaxDelay() {
        return mMaxDelay;
    }

    /**
     * Returns true if the sensor is a wake-up sensor.
     * <p>
     * <b>Application Processor Power modes</b> <p>
     * Application Processor(AP), is the processor on which applications run.  When no wake lock is held
     * and the user is not interacting with the device, this processor can enter a “Suspend” mode,
     * reducing the power consumption by 10 times or more.
     * </p>
     * <p>
     * <b>Non-wake-up sensors</b> <p>
     * Non-wake-up sensors are sensors that do not wake the AP out of suspend to report data. While
     * the AP is in suspend mode, the sensors continue to function and generate events, which are
     * put in a hardware FIFO. The events in the FIFO are delivered to the application when the AP
     * wakes up. If the FIFO was too small to store all events generated while the AP was in
     * suspend mode, the older events are lost: the oldest data is dropped to accommodate the newer
     * data. In the extreme case where the FIFO is non-existent {@code maxFifoEventCount() == 0},
     * all events generated while the AP was in suspend mode are lost. Applications using
     * non-wake-up sensors should usually:
     * <ul>
     * <li>Either unregister from the sensors when they do not need them, usually in the activity’s
     * {@code onPause} method. This is the most common case.
     * <li>Or realize that the sensors are consuming some power while the AP is in suspend mode and
     * that even then, some events might be lost.
     * </ul>
     * </p>
     * <p>
     * <b>Wake-up sensors</b> <p>
     * In opposition to non-wake-up sensors, wake-up sensors ensure that their data is delivered
     * independently of the state of the AP. While the AP is awake, the wake-up sensors behave
     * like non-wake-up-sensors. When the AP is asleep, wake-up sensors wake up the AP to deliver
     * events. That is, the AP will wake up and the sensor will deliver the events before the
     * maximum reporting latency is elapsed or the hardware FIFO gets full. See {@link
     * SensorManager#registerListener(SensorEventListener, Sensor, int, int)} for more details.
     * </p>
     *
     * @return <code>true</code> if this is a wake-up sensor, <code>false</code> otherwise.
     */
    public boolean isWakeUpSensor() {
        return (mFlags & SENSOR_FLAG_WAKE_UP_SENSOR) != 0;
    }

    /**
     * Returns true if the sensor supports data injection when the
     * HAL is set to data injection mode.
     *
     * @return <code>true</code> if the sensor supports data
     *         injection when the HAL is set in injection mode,
     *         false otherwise.
     * @hide
     */
    @SystemApi
    public boolean isDataInjectionSupported() {
        return (((mFlags & DATA_INJECTION_MASK) >> DATA_INJECTION_SHIFT)) != 0;
    }

    void setRange(float max, float res) {
        mMaxRange = max;
        mResolution = res;
    }

    @Override
    public String toString() {
        return "{Sensor name=\"" + mName + "\", vendor=\"" + mVendor + "\", version=" + mVersion
                + ", type=" + mType + ", maxRange=" + mMaxRange + ", resolution=" + mResolution
                + ", power=" + mPower + ", minDelay=" + mMinDelay + "}";
    }

    /**
     * Sets the Type associated with the sensor.
     * NOTE: to be used only by native bindings in SensorManager.
     *
     * This allows interned static strings to be used across all representations of the Sensor. If
     * a sensor type is not referenced here, it will still be interned by the native SensorManager.
     *
     * @return {@code true} if the StringType was successfully set, {@code false} otherwise.
     */
    private boolean setType(int value) {
        mType = value;
        switch (mType) {
            case TYPE_ACCELEROMETER:
                mStringType = STRING_TYPE_ACCELEROMETER;
                return true;
            case TYPE_AMBIENT_TEMPERATURE:
                mStringType = STRING_TYPE_AMBIENT_TEMPERATURE;
                return true;
            case TYPE_GAME_ROTATION_VECTOR:
                mStringType = STRING_TYPE_GAME_ROTATION_VECTOR;
                return true;
            case TYPE_GEOMAGNETIC_ROTATION_VECTOR:
                mStringType = STRING_TYPE_GEOMAGNETIC_ROTATION_VECTOR;
                return true;
            case TYPE_GLANCE_GESTURE:
                mStringType = STRING_TYPE_GLANCE_GESTURE;
                return true;
            case TYPE_GRAVITY:
                mStringType = STRING_TYPE_GRAVITY;
                return true;
            case TYPE_GYROSCOPE:
                mStringType = STRING_TYPE_GYROSCOPE;
                return true;
            case TYPE_GYROSCOPE_UNCALIBRATED:
                mStringType = STRING_TYPE_GYROSCOPE_UNCALIBRATED;
                return true;
            case TYPE_HEART_RATE:
                mStringType = STRING_TYPE_HEART_RATE;
                return true;
            case TYPE_LIGHT:
                mStringType = STRING_TYPE_LIGHT;
                return true;
            case TYPE_LINEAR_ACCELERATION:
                mStringType = STRING_TYPE_LINEAR_ACCELERATION;
                return true;
            case TYPE_MAGNETIC_FIELD:
                mStringType = STRING_TYPE_MAGNETIC_FIELD;
                return true;
            case TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                mStringType = STRING_TYPE_MAGNETIC_FIELD_UNCALIBRATED;
                return true;
            case TYPE_PICK_UP_GESTURE:
                mStringType = STRING_TYPE_PICK_UP_GESTURE;
                return true;
            case TYPE_PRESSURE:
                mStringType = STRING_TYPE_PRESSURE;
                return true;
            case TYPE_PROXIMITY:
                mStringType = STRING_TYPE_PROXIMITY;
                return true;
            case TYPE_RELATIVE_HUMIDITY:
                mStringType = STRING_TYPE_RELATIVE_HUMIDITY;
                return true;
            case TYPE_ROTATION_VECTOR:
                mStringType = STRING_TYPE_ROTATION_VECTOR;
                return true;
            case TYPE_SIGNIFICANT_MOTION:
                mStringType = STRING_TYPE_SIGNIFICANT_MOTION;
                return true;
            case TYPE_STEP_COUNTER:
                mStringType = STRING_TYPE_STEP_COUNTER;
                return true;
            case TYPE_STEP_DETECTOR:
                mStringType = STRING_TYPE_STEP_DETECTOR;
                return true;
            case TYPE_TILT_DETECTOR:
                mStringType = SENSOR_STRING_TYPE_TILT_DETECTOR;
                return true;
            case TYPE_WAKE_GESTURE:
                mStringType = STRING_TYPE_WAKE_GESTURE;
                return true;
            case TYPE_ORIENTATION:
                mStringType = STRING_TYPE_ORIENTATION;
                return true;
            case TYPE_TEMPERATURE:
                mStringType = STRING_TYPE_TEMPERATURE;
                return true;
            default:
                return false;
        }
    }
}
