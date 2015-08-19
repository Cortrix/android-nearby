/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gms.nearby.messages.samples.nearbydevices;

import android.content.IntentSender;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.iid.InstanceID;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.NearbyMessagesStatusCodes;
import com.google.android.gms.nearby.messages.Strategy;

import java.util.ArrayList;

public class MainFragment extends Fragment implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    static final int REQUEST_RESOLVE_ERROR = 1001;
    static final int TTL_IN_SECONDS = 3 * 60;

    private static final Strategy PUB_STRATEGY = new Strategy.Builder()
            .setTtlSeconds(TTL_IN_SECONDS)
            .setDistanceType(Strategy.DISTANCE_TYPE_EARSHOT)
            .setDiscoveryMode(Strategy.DISCOVERY_MODE_BROADCAST)
            .build();

    private static final Strategy SUB_STRATEGY = new Strategy.Builder()
            .setTtlSeconds(TTL_IN_SECONDS)
            .setDistanceType(Strategy.DISTANCE_TYPE_EARSHOT)
            .setDiscoveryMode(Strategy.DISCOVERY_MODE_SCAN)
            .build();

    private ProgressBar mSubscriptionProgressBar;
    private ImageButton mSubscriptionImageButton;
    private ProgressBar mPublicationProgressBar;
    private ImageButton mPublicationImageButton;

    private ArrayAdapter<String> mNearbyDevicesArrayAdapter;
    private final ArrayList<String> mNearbyDevicesArrayList = new ArrayList<>();

    private ArrayAdapter<String> mLogArrayAdapter;
    private final ArrayList<String> mLogArrayList = new ArrayList<>();

    private GoogleApiClient mGoogleApiClient;

    private MessageListener mMessageListener;

    private enum State {
        ACTIVE,
        ACTIVATING,
        INACTIVE,
        INACTIVATING
    }

    private State mPublishState = State.INACTIVE;
    private State mSubscribeState = State.INACTIVE;

    public MainFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use a retained fragment to avoid re-publishing or re-subscribing upon orientation
        // changes.
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        mSubscriptionProgressBar = (ProgressBar) view.findViewById(R.id.subscription_progress_bar);
        mSubscriptionImageButton = (ImageButton) view.findViewById(R.id.subscription_image_button);
        mSubscriptionImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch(mSubscribeState) {
                    case INACTIVE:
                        subscribe();
                        break;
                    case ACTIVE:
                        unsubscribe();
                        break;
                }
            }
        });

        mPublicationProgressBar = (ProgressBar) view.findViewById(R.id.publication_progress_bar);
        mPublicationImageButton = (ImageButton) view.findViewById(R.id.publication_image_button);
        mPublicationImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch(mPublishState) {
                    case INACTIVE:
                        publish();
                        break;
                    case ACTIVE:
                        unpublish();
                        break;
                }
            }
        });

        ListView nearbyDevicesListView = (ListView) view.findViewById(R.id.nearby_devices_list_view);
        mNearbyDevicesArrayAdapter = new ArrayAdapter<>(getActivity().getApplicationContext(),
                android.R.layout.simple_list_item_1, mNearbyDevicesArrayList);
        nearbyDevicesListView.setAdapter(mNearbyDevicesArrayAdapter);

        ListView logListView = (ListView) view.findViewById(R.id.log_list_view);
        mLogArrayAdapter = new ArrayAdapter<>(getActivity().getApplicationContext(),
                android.R.layout.simple_list_item_1, mLogArrayList);
        logListView.setAdapter(mLogArrayAdapter);

        mMessageListener = new MessageListener() {
            @Override
            public void onFound(final Message message) {
                mNearbyDevicesArrayAdapter.add(DeviceMessage.fromNearbyMessage(message).getMessageBody());
            }

            @Override
            public void onLost(final Message message) {
                mNearbyDevicesArrayAdapter.remove(DeviceMessage.fromNearbyMessage(message).getMessageBody());
            }
        };

        // Upon orientation change, ensure that the state of the UI is maintained.
        updateUI();
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        mGoogleApiClient = new GoogleApiClient.Builder(getActivity().getApplicationContext())
                .addApi(Nearby.MESSAGES_API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public void onStop() {
        if (mGoogleApiClient.isConnected() && !getActivity().isChangingConfigurations()) {

            if (mSubscribeState == State.ACTIVE) {
                unsubscribe();
            }

            if (mPublishState == State.ACTIVE) {
                unpublish();
            }

            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        mLogArrayAdapter.add("GoogleApiClient connected");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        mLogArrayAdapter.add("GoogleApiClient connection suspended: "
                + connectionSuspendedCauseToString(cause));
    }

    private static String connectionSuspendedCauseToString(int cause) {
        switch (cause) {
            case CAUSE_NETWORK_LOST:
                return "CAUSE_NETWORK_LOST";
            case CAUSE_SERVICE_DISCONNECTED:
                return "CAUSE_SERVICE_DISCONNECTED";
            default:
                return "CAUSE_UNKNOWN: " + cause;
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // For simplicity, we don't handle connection failure thoroughly in this sample. Refer to
        // the following Google Play services doc for more details:
        // http://developer.android.com/google/auth/api-client.html
        mLogArrayAdapter.add("connection to GoogleApiClient failed");
    }

    /**
     * Subscribes to messages from nearby devices. If successful, enqueues a
     * {@link android.os.Message} that is sent to the {@code ResetHandler} when the subscription
     * ends, which then updates state. If not successful, attempts to resolve any error related to
     * Nearby permissions by displaying an opt-in dialog.
     */
    private void subscribe() {
        mSubscribeState = State.ACTIVATING;
        updateUI();
        mLogArrayAdapter.add("trying to subscribe");
        Nearby.Messages.subscribe(mGoogleApiClient, mMessageListener, SUB_STRATEGY)
                .setResultCallback(new ResultCallback<Status>() {

                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            mLogArrayAdapter.add("subscribed successfully");
                            mSubscribeState = State.ACTIVE;
                            updateUI();
                        } else {
                            mLogArrayAdapter.add("could not subscribe");
                            mSubscribeState = State.INACTIVE;
                            updateUI();

                            handleUnsuccessfulNearbyResult(status);
                        }
                    }
                });
    }


    /**
     * Ends the subscription to messages from nearby devices. If successful, resets state. If not
     * successful, attempts to resolve any error related to Nearby permissions by
     * displaying an opt-in dialog.
     */
    private void unsubscribe() {
        mLogArrayAdapter.add("trying to unsubscribe");
        mNearbyDevicesArrayAdapter.clear();
        mSubscribeState = State.INACTIVATING;
        updateUI();
        Nearby.Messages.unsubscribe(mGoogleApiClient, mMessageListener)
                .setResultCallback(new ResultCallback<Status>() {

                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            mLogArrayAdapter.add("unsubscribed successfully");
                            mSubscribeState = State.INACTIVE;
                            updateUI();
                        } else {
                            mLogArrayAdapter.add("could not unsubscribe");
                            mSubscribeState = State.ACTIVE;
                            updateUI();

                            handleUnsuccessfulNearbyResult(status);
                        }
                    }
                });
    }

    /**
     * Publishes device information to nearby devices. If successful, enqueues a
     * {@link android.os.Message} that is sent to the {@code ResetHandler} when the publication
     * ends, which updates state. If not successful, attempts to resolve any error related to
     * Nearby permissions by displaying an opt-in dialog.
     */
    private void publish() {
        mLogArrayAdapter.add("trying to publish");
        Message deviceInfoMessage = DeviceMessage.newNearbyMessage(
                InstanceID.getInstance(getActivity().getApplicationContext()).getId());

        mPublishState = State.ACTIVATING;
        updateUI();
        Nearby.Messages.publish(mGoogleApiClient, deviceInfoMessage, PUB_STRATEGY)
                .setResultCallback(new ResultCallback<Status>() {

                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            mLogArrayAdapter.add("published successfully");
                            mPublishState = State.ACTIVE;
                            updateUI();
                        } else {
                            mLogArrayAdapter.add("could not publish");
                            mPublishState = State.INACTIVE;
                            updateUI();

                            handleUnsuccessfulNearbyResult(status);
                        }
                    }
                });
    }

    /**
     * Stops publishing device information to nearby devices. If successful, resets state. If not
     * successful, attempts to resolve any error related to Nearby permissions by displaying an
     * opt-in dialog.
     */
    private void unpublish() {
        mLogArrayAdapter.add("trying to unpublish");

        Message mDeviceInfoMessage = DeviceMessage.newNearbyMessage(
                InstanceID.getInstance(getActivity().getApplicationContext()).getId());

        mPublishState = State.INACTIVATING;
        updateUI();
        Nearby.Messages.unpublish(mGoogleApiClient, mDeviceInfoMessage)
                .setResultCallback(new ResultCallback<Status>() {

                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            mLogArrayAdapter.add("unpublished successfully");
                            mPublishState = State.INACTIVE;
                            updateUI();
                        } else {
                            mLogArrayAdapter.add("could not unpublish");
                            mPublishState = State.ACTIVE;
                            updateUI();

                            handleUnsuccessfulNearbyResult(status);
                        }
                    }
                });
    }

    /**
     * Handles errors generated when performing a subscription or publication action. Uses
     * {@link Status#startResolutionForResult} to display an opt-in dialog to handle the case
     * where a device is not opted into using Nearby.
     */
    private void handleUnsuccessfulNearbyResult(Status status) {
        mLogArrayAdapter.add("processing error, status = " + status);
        if (status.getStatusCode() == NearbyMessagesStatusCodes.APP_NOT_OPTED_IN) {
            try {
                status.startResolutionForResult(getActivity(), REQUEST_RESOLVE_ERROR);

            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            if (status.getStatusCode() == ConnectionResult.NETWORK_ERROR) {
                mLogArrayAdapter.add("No connectivity, cannot proceed. Fix in 'Settings' and try again.");
            } else {
                mLogArrayAdapter.add("Unsuccessful: " + status.getStatusMessage());
            }

        }
    }

    /**
     * Updates the UI when the state of a subscription or publication action changes.
     */
    private void updateUI() {

        mSubscriptionProgressBar.setVisibility(mSubscribeState != State.INACTIVE
                        ? View.VISIBLE : View.INVISIBLE);

        mPublicationProgressBar.setVisibility(mPublishState != State.INACTIVE
                ? View.VISIBLE : View.INVISIBLE);

        mSubscriptionImageButton.setImageResource(mSubscribeState != State.INACTIVE
                        ? R.drawable.ic_cancel : R.drawable.ic_nearby);

        mPublicationImageButton.setImageResource(mPublishState != State.INACTIVE
                        ? R.drawable.ic_cancel : R.drawable.ic_share);
    }
}