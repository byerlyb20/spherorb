package com.badonlabs.brigham.spherorb;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.InputDevice;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.orbotix.ConvenienceRobot;
import com.orbotix.DualStackDiscoveryAgent;
import com.orbotix.async.CollisionDetectedAsyncData;
import com.orbotix.classic.RobotClassic;
import com.orbotix.command.RollCommand;
import com.orbotix.common.DiscoveryException;
import com.orbotix.common.ResponseListener;
import com.orbotix.common.Robot;
import com.orbotix.common.RobotChangedStateListener;
import com.orbotix.common.internal.AsyncMessage;
import com.orbotix.common.internal.DeviceResponse;
import com.orbotix.le.RobotLE;

public class Spherorb extends AppCompatActivity {

    private ConvenienceRobot mRobot;
    private InputDevice mGamepad;
    private CoordinatorLayout mParentLayout;
    private LinearLayout mStats;
    private TextView mStatus;
    private TextView mProcessedStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spherorb);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DualStackDiscoveryAgent.getInstance().addRobotStateListener(new RobotChangedStateListener() {
            @Override
            public void handleRobotChangedState(Robot robot, RobotChangedStateNotificationType type) {
                ImageView sphero = (ImageView) findViewById(R.id.sphero);
                switch (type) {
                    case Online:
                        if (robot instanceof RobotClassic || robot instanceof RobotLE) {
                            showMessage(R.string.event_connected);
                            if (robot instanceof RobotLE) {
                                sphero.setImageResource(R.drawable.ollie_blue);
                            } else {
                                sphero.setImageResource(R.drawable.sphero_blue);
                            }
                            mRobot = new ConvenienceRobot(robot);
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
                                    if (asyncMessage instanceof CollisionDetectedAsyncData) {
                                        if (mGamepad != null) {
                                            mGamepad.getVibrator().vibrate(500);
                                        }
                                        showMessage(R.string.event_collision);
                                    }
                                }
                            });
                        } else {
                            showMessage(R.string.event_not_compatible);
                        }
                        break;
                    case Disconnected:
                        showMessage(R.string.event_disconnected);
                        sphero.setImageResource(R.drawable.sphero_grey);
                        break;
                }
            }
        });
        mParentLayout = (CoordinatorLayout) findViewById(R.id.parentLayout);
        mStats = (LinearLayout) findViewById(R.id.stats);
        mStatus = (TextView) findViewById(R.id.controllerData);
        mProcessedStatus = (TextView) findViewById(R.id.robotData);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_spherorb, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_toggle_stats:
                if (item.isChecked()) {
                    item.setChecked(false);
                    mStats.setVisibility(View.GONE);
                } else {
                    item.setChecked(true);
                    mStats.setVisibility(View.VISIBLE);
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        InputDevice device = event.getDevice();
        if (isGameController(device)) {
            mGamepad = device;

            for (int i = 0; i < event.getHistorySize(); i++) {
                processJoystickInput(event, i);
            }

            processJoystickInput(event, -1);
            return true;
        }

        return super.onGenericMotionEvent(event);
    }

    private boolean isGameController(InputDevice device) {
        int sources = device.getSources();
        return ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                || ((sources & InputDevice.SOURCE_JOYSTICK)
                == InputDevice.SOURCE_JOYSTICK);
    }

    private float getCenteredAxis(MotionEvent event,
                                  InputDevice device, int axis, int historyPos) {
        final InputDevice.MotionRange range =
                device.getMotionRange(axis, event.getSource());

        // A joystick at rest does not always report an absolute position of
        // (0,0). Use the getFlat() method to determine the range of values
        // bounding the joystick axis center.
        if (range != null) {
            final float flat = range.getFlat();
            float value = historyPos < 0 ? event.getAxisValue(axis) :
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
        float x = getCenteredAxis(event, mGamepad, MotionEvent.AXIS_X, historyPos);
        float y = getCenteredAxis(event, mGamepad, MotionEvent.AXIS_Y, historyPos);
        float x2 = getCenteredAxis(event, mGamepad, MotionEvent.AXIS_Z, historyPos);
        float y2 = getCenteredAxis(event, mGamepad, MotionEvent.AXIS_RZ, historyPos);

        Resources res = getResources();
        String output = res.getString(R.string.stats_controller, x, y);
        mStatus.setText(output);

        onControllerMoved(x, y);
        onCalibrateRobot(x2, y2);
    }

    protected void onCalibrateRobot(float x, float y) {
        if (mRobot != null) {
            float heading = getHeading(x, -y);
            mRobot.drive(heading, 0);
            mRobot.setZeroHeading();
        }
    }

    protected void onControllerMoved(float x, float y) {
        float velocity = getVelocity(x, y);
        float heading = getHeading(x, -y);

        Resources res = getResources();
        String output = res.getString(R.string.stats_robot, velocity, heading);
        mProcessedStatus.setText(output);

        if (mRobot != null) {
            if (y == 0) {
                mRobot.sendCommand(new RollCommand(mRobot.getLastHeading(), 0.0f,
                        RollCommand.State.STOP));
            } else {
                mRobot.sendCommand(new RollCommand(heading, velocity, RollCommand.State.GO));
            }
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
        // Maybe clip values to about .8
        y = Math.abs(y);
        x = Math.abs(x);

        //float slope = y / x;
        float originDistance = (float) Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
        //float maxOriginDistance = (float) Math.sqrt(1 + Math.pow((slope > 1 ? 1 / slope : slope), 2));
        float maxOriginDistance = 1;

        return originDistance / maxOriginDistance;
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
        if (mRobot != null) {
            mRobot.disconnect();
        }
        super.onStop();
    }

    private void showMessage(int msg) {
        Snackbar.make(mParentLayout, msg, Snackbar.LENGTH_SHORT)
                .show();
    }
}
