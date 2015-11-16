package com.badonlabs.brigham.spherorb;

import android.hardware.input.InputManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

import com.orbotix.DualStackDiscoveryAgent;
import com.orbotix.Sphero;
import com.orbotix.async.CollisionDetectedAsyncData;
import com.orbotix.classic.RobotClassic;
import com.orbotix.command.RollCommand;
import com.orbotix.common.DiscoveryException;
import com.orbotix.common.ResponseListener;
import com.orbotix.common.Robot;
import com.orbotix.common.RobotChangedStateListener;
import com.orbotix.common.internal.AsyncMessage;
import com.orbotix.common.internal.DeviceResponse;

public class Spherorb extends AppCompatActivity {

    private Sphero mRobot;
    private InputDevice mGamepad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spherorb);

        DualStackDiscoveryAgent.getInstance().addRobotStateListener(new RobotChangedStateListener() {
            @Override
            public void handleRobotChangedState(Robot robot, RobotChangedStateNotificationType type) {
                switch (type) {
                    case Online:
                        Toast.makeText(Spherorb.this, "Connected", Toast.LENGTH_SHORT).show();
                        if (robot instanceof RobotClassic) {
                            mRobot = new Sphero(robot);
                            mRobot.enableStabilization(true);
                            mRobot.enableCollisions(true);
                            mRobot.addResponseListener(new ResponseListener() {
                                @Override
                                public void handleResponse(DeviceResponse deviceResponse, Robot robot) {

                                }

                                @Override
                                public void handleStringResponse(String s, Robot robot) {

                                }

                                @Override
                                public void handleAsyncMessage(AsyncMessage asyncMessage, Robot robot) {
                                    if(asyncMessage instanceof CollisionDetectedAsyncData) {
                                        if (mGamepad != null) {
                                            mGamepad.getVibrator().vibrate(500);
                                        }
                                    }
                                }
                            });
                        } else {
                            Toast.makeText(Spherorb.this, "Not Compatible", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case Disconnected:
                        break;
                }
            }
        });
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {

        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) ==
                InputDevice.SOURCE_JOYSTICK &&
                event.getAction() == MotionEvent.ACTION_MOVE) {

            mGamepad = event.getDevice();

            final int historySize = event.getHistorySize();

            for (int i = 0; i < historySize; i++) {
                processJoystickInput(event, i);
            }

            processJoystickInput(event, -1);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private static float getCenteredAxis(MotionEvent event,
                                         InputDevice device, int axis, int historyPos) {
        final InputDevice.MotionRange range =
                device.getMotionRange(axis, event.getSource());

        // A joystick at rest does not always report an absolute position of
        // (0,0). Use the getFlat() method to determine the range of values
        // bounding the joystick axis center.
        if (range != null) {
            final float flat = range.getFlat();
            float value =
                    historyPos < 0 ? event.getAxisValue(axis) :
                            event.getHistoricalAxisValue(axis, historyPos);

            // Ignore axis values that are within the 'flat' region of the
            // joystick axis center.
            if (Math.abs(value) > flat) {
                value = axis == MotionEvent.AXIS_Y ? -value : value;
                return value;
            }
        }
        return 0;
    }

    private void processJoystickInput(MotionEvent event, int historyPos) {

        InputDevice mInputDevice = event.getDevice();

        float x = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_X, historyPos);

        float y = getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_Y, historyPos);

        onControllerMoved(x, y);
    }

    protected void onControllerMoved(float x, float y) {
        TextView status = (TextView) findViewById(R.id.status);
        String output = "X Value: " + x + ", Y Value: " + y;
        status.setText(output);

        if (y == 0) {
            if (mRobot != null)
                mRobot.sendCommand(new RollCommand(mRobot.getLastHeading(), 0.0f, RollCommand.State.STOP));
        } else {
            float velocity = getVelocity(x, y);
            float heading = getHeading(x, -y);

            if (mRobot != null)
                mRobot.sendCommand(new RollCommand(heading, velocity, RollCommand.State.GO));
            output += ", Heading: " + heading + ", Velocity: " + velocity;
            status.setText(output);
        }
    }

    private float getHeading(float x, float y) {
        float val = (float) Math.toDegrees(Math.atan2(y, x));
        val -= 315;

        if (val < 0) {
            val += 405;
        }

        if (val < 0) {
            val = 360 - Math.abs(val);
        }
        return val;
    }

    private float getVelocity(float x, float y) {
        y = Math.abs(y);
        x = Math.abs(x);

        float slope = y / x;
        float orginDistance = (float) Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
        float maxOrginDistance = (float) Math.sqrt(1 + Math.pow((slope > 1 ? 1 / slope : slope), 2));

        return orginDistance / maxOrginDistance;
    }

    @Override
    protected void onStart() {
        super.onStart();

        try {
            DualStackDiscoveryAgent.getInstance().startDiscovery(this);
        } catch (DiscoveryException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStop() {
        if (mRobot != null) mRobot.disconnect();
        super.onStop();
    }
}
