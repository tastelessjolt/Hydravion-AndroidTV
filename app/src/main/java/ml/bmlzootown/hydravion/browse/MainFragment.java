package ml.bmlzootown.hydravion.browse;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ImageCardView;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import com.android.volley.VolleyError;
import com.google.gson.Gson;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.Unit;
import ml.bmlzootown.hydravion.CardPresenter;
import ml.bmlzootown.hydravion.Constants;
import ml.bmlzootown.hydravion.R;
import ml.bmlzootown.hydravion.RequestTask;
import ml.bmlzootown.hydravion.client.HydravionClient;
import ml.bmlzootown.hydravion.detail.DetailsActivity;
import ml.bmlzootown.hydravion.login.LoginActivity;
import ml.bmlzootown.hydravion.models.Edge;
import ml.bmlzootown.hydravion.models.Edges;
import ml.bmlzootown.hydravion.models.Live;
import ml.bmlzootown.hydravion.models.Video;
import ml.bmlzootown.hydravion.playback.PlaybackActivity;
import ml.bmlzootown.hydravion.subscription.Subscription;
import ml.bmlzootown.hydravion.subscription.SubscriptionHeaderPresenter;

public class MainFragment extends BrowseSupportFragment {

    private static final String TAG = "MainFragment";

    private static final int BACKGROUND_UPDATE_DELAY = 300;
    private static final int GRID_ITEM_WIDTH = 200;
    private static final int GRID_ITEM_HEIGHT = 200;
    private static int NUM_ROWS = 6;
    private static int NUM_COLS = 15;

    private HydravionClient client;
    private final Handler mHandler = new Handler();
    private Drawable mDefaultBackground;
    private DisplayMetrics mMetrics;
    private Timer mBackgroundTimer;
    private String mBackgroundUri;
    private BackgroundManager mBackgroundManager;

    public static String sailssid;
    public static String cfduid;
    public static String cdn;

    public static List<Subscription> subscriptions = new ArrayList<>();
    public static HashMap<String, ArrayList<Video>> videos = new HashMap<>();
    private int subCount;
    private int page = 1;

    private int rowSelected;
    private int colSelected;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onActivityCreated(savedInstanceState);
        client = HydravionClient.Companion.getInstance(requireActivity(), requireActivity().getPreferences(Context.MODE_PRIVATE));
        checkLogin();
        //test();

        //prepareBackgroundManager();

        //setupUIElements();

        //setupEventListeners();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != mBackgroundTimer) {
            Log.d(TAG, "onDestroy: " + mBackgroundTimer.toString());
            mBackgroundTimer.cancel();
        }
    }

    private void checkLogin() {
        boolean gotCookies = loadCredentials();
        if (!gotCookies) {
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivityForResult(intent, 42);
            cdn = "edge03-na.floatplane.com";
        } else {
            initialize();
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 42 && resultCode == 1 && data != null) {
            ArrayList<String> cookies = data.getStringArrayListExtra("cookies");
            for (String cookie : cookies) {
                String[] c = cookie.split("=");
                if (c[0].equalsIgnoreCase("sails.sid")) {
                    sailssid = c[1];
                }
                if (c[0].equalsIgnoreCase("__cfduid")) {
                    cfduid = c[1];
                }
            }
            Log.d("MainFragment", cfduid + "; " + sailssid);

            saveCredentials();
            initialize();
        }
    }

    private void initialize() {
        client.getSubs(subscriptions -> {
            if (subscriptions == null) {
                new AlertDialog.Builder(getContext())
                        .setTitle("Session Expired")
                        .setMessage("Re-open Hydravion to login again!")
                        .setPositiveButton("OK",
                                (dialog, which) -> {
                                    dialog.dismiss();
                                    logout();
                                })
                        .create()
                        .show();
            } else {
                gotSubscriptions(subscriptions);
            }

            return Unit.INSTANCE;
        });
        prepareBackgroundManager();
        setupUIElements();
        setupEventListeners();
    }

    private boolean loadCredentials() {
        SharedPreferences prefs = getActivity().getPreferences(Context.MODE_PRIVATE);
        sailssid = prefs.getString(Constants.PREF_SAIL_SSID, "default");
        cfduid = prefs.getString(Constants.PREF_CFD_UID, "default");
        cdn = prefs.getString(Constants.PREF_CDN, "default");
        Log.d("LOGIN", sailssid);
        Log.d("LOGIN", cfduid);
        Log.d("CDN", cdn);
        if (sailssid.equals("default") || cfduid.equals("default") || cdn.equals("default")) {
            Log.d("LOGIN", "Credentials not found!");
            return false;
        } else {
            Log.d("LOGIN", "Credentials found!");
            return true;
        }
    }

    private void logout() {
        sailssid = "default";
        cfduid = "default";
        saveCredentials();
        getActivity().finishAndRemoveTask();
    }

    private void saveCredentials() {
        SharedPreferences prefs = getActivity().getPreferences(Context.MODE_PRIVATE);
        prefs.edit().putString(Constants.PREF_SAIL_SSID, sailssid).apply();
        prefs.edit().putString(Constants.PREF_CFD_UID, cfduid).apply();
        prefs.edit().putString(Constants.PREF_CDN, cdn).apply();
    }

    private void gotLiveInfo(Subscription sub, Live live) {
        String l = live.getCdn() + live.getResource().getUri();
        String pattern = "\\{(.*?)\\}";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(live.getResource().getUri());
        if (m.find()) {
            for (int i = 0; i < m.groupCount(); i++) {
                //Log.d("LIVE", m.group(i));
                String var = m.group(i).substring(1, m.group(i).length() - 1);
                if (var.equalsIgnoreCase("token")) {
                    l = l.replaceAll("\\{token\\}", live.getResource().getData().getToken());
                    sub.setStreamUrl(l);
                    Log.d("LIVE", l);
                }
                //Log.d("LIVE", l);
            }
        }
    }

    private void gotSubscriptions(Subscription[] subs) {
        NUM_ROWS = subs.length;
        List<Subscription> trimmed = new ArrayList<>();
        for (Subscription sub : subs) {
            if (trimmed.size() > 0) {
                if (!containsSub(trimmed, sub)) {
                    trimmed.add(sub);
                }
            } else {
                trimmed.add(sub);
            }
        }
        subscriptions = trimmed;
        for (Subscription sub : subscriptions) {
            client.getLive(sub.getCreator(), live -> {
                gotLiveInfo(sub, live);
                return Unit.INSTANCE;
            });
            client.getVideos(sub.getCreator(), 1, videos -> {
                gotVideos(sub.getCreator(), videos);
                return Unit.INSTANCE;
            });
        }
        subCount = trimmed.size();
        Log.d("ROWS", trimmed.size() + "");
    }

    private boolean containsSub(List<Subscription> trimmed, Subscription sub) {
        for (Subscription s : trimmed) {
            if (s.getCreator().equals(sub.getCreator())) {
                return true;
            }
        }
        return false;
    }

    private void gotVideos(String creatorGUID, Video[] vids) {
        if (videos.get(creatorGUID) != null && videos.get(creatorGUID).size() > 0) {
            videos.get(creatorGUID).addAll(Arrays.asList(vids));
        } else {
            videos.put(creatorGUID, new ArrayList<>(Arrays.asList(vids)));
        }


        if (subCount > 1) {
            subCount--;
        } else {
            refreshRows();
            subCount = subscriptions.size();
            setSelectedPosition(rowSelected, false, new ListRowPresenter.SelectItemViewHolderTask(colSelected));
        }

        NUM_COLS = videos.get(creatorGUID).size();
    }

    private void refreshRows() {
        List<Subscription> subs = subscriptions;
        ArrayObjectAdapter rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        CardPresenter cardPresenter = new CardPresenter();

        int i;
        for (i = 0; i < subs.size(); i++) {
            ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(cardPresenter);

            Subscription sub = subscriptions.get(i);
            List<Video> vids = videos.get(sub.getCreator());

            if (vids != null) {
                vids.forEach(listRowAdapter::add);
            }

            HeaderItem header = new HeaderItem(i, sub.getPlan().getTitle());
            rowsAdapter.add(new ListRow(header, listRowAdapter));
        }

        HeaderItem gridHeader = new HeaderItem(i, getString(R.string.settings));

        GridItemPresenter mGridPresenter = new GridItemPresenter();
        ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(mGridPresenter);
        gridRowAdapter.add(getResources().getString(R.string.refresh));
        gridRowAdapter.add(getResources().getString(R.string.live_stream));
        gridRowAdapter.add(getResources().getString(R.string.select_server));
        gridRowAdapter.add(getResources().getString(R.string.logout));
        rowsAdapter.add(new ListRow(gridHeader, gridRowAdapter));

        setAdapter(rowsAdapter);
    }

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(requireActivity());
        mBackgroundManager.attach(requireActivity().getWindow());

        mDefaultBackground = ContextCompat.getDrawable(requireActivity(), R.drawable.default_background);
        mMetrics = new DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void setupUIElements() {
        // setBadgeDrawable(getActivity().getResources().getDrawable(R.drawable.videos_by_google_banner));
        setBadgeDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.white_plane));
        //setTitle(getString(R.string.browse_title)); // Badge, when set, takes precedent
        // over title
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);
        setHeaderPresenterSelector(new PresenterSelector() {
            @Override
            public Presenter getPresenter(Object item) {
                return new SubscriptionHeaderPresenter();
            }
        });

        // set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(requireContext(), R.color.fastlane_background));
        // set search icon color
        //setSearchAffordanceColor(ContextCompat.getColor(getContext(), R.color.search_opaque));
    }

    private void setupEventListeners() {
        /*setOnSearchClickedListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Toast.makeText(getActivity(), "Implement your own in-app search", Toast.LENGTH_LONG)
                        .show();
            }
        });*/

        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
    }

    private void updateBackground(String uri) {
        int width = mMetrics.widthPixels;
        int height = mMetrics.heightPixels;
        /*Glide.with(getActivity())
                .load(uri)
                .centerCrop()
                .error(mDefaultBackground)
                .transition(
                        new DrawableTransitionOptions().crossFade())
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        mBackgroundManager.setDrawable(resource);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {

                    }
                });*/
        mBackgroundTimer.cancel();
    }

    private void startBackgroundTimer() {
        if (null != mBackgroundTimer) {
            mBackgroundTimer.cancel();
        }
        mBackgroundTimer = new Timer();
        mBackgroundTimer.schedule(new UpdateBackgroundTask(), BACKGROUND_UPDATE_DELAY);
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {
            if (item instanceof Video) {
                getSelectVid(itemViewHolder, item);
            } else if (item instanceof String) {
                if (item.toString().equalsIgnoreCase(getString(R.string.refresh))) {
                    refreshRows();
                } else if (item.toString().equalsIgnoreCase(getString(R.string.logout))) {
                    //sailssid = "default";
                    //cfduid = "default";
                    //saveCredentials();
                    //getActivity().finishAndRemoveTask();
                    logout();
                } else if (item.toString().equalsIgnoreCase(getString(R.string.select_server))) {
                    String uri = "https://www.floatplane.com/api/edges";
                    String cookies = "__cfduid=" + MainFragment.cfduid + "; sails.sid=" + MainFragment.sailssid;
                    RequestTask rt = new RequestTask(getActivity().getApplicationContext());
                    rt.sendRequest(uri, cookies, new RequestTask.VolleyCallback() {
                        @Override
                        public void onSuccess(String string) {
                            Gson gson = new Gson();
                            Edges es = gson.fromJson(string, Edges.class);
                            List<String> servers = new ArrayList<>();
                            if (es != null) {
                                List<Edge> edges = es.getEdges();
                                for (Edge e : edges) {
                                    if (e.getAllowStreaming()) {
                                        servers.add(e.getHostname());
                                    }
                                }
                                CharSequence[] hostnames = servers.toArray(new CharSequence[servers.size()]);
                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                builder.setTitle("Select CDN Server");
                                builder.setItems(hostnames,
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                String server = servers.get(which);
                                                Log.d("CDN", server);
                                                SharedPreferences prefs = getActivity().getPreferences(Context.MODE_PRIVATE);
                                                prefs.edit().putString("cdn", server).apply();
                                            }
                                        });
                                builder.create().show();
                            }
                        }

                        @Override
                        public void onSuccessCreator(String string, String creatorGUID) {
                        }

                        @Override
                        public void onError(VolleyError error) {
                        }
                    });
                } else if (item.toString().equalsIgnoreCase(getString(R.string.live_stream))) {
                    List<String> subs = new ArrayList<>();
                    for (Subscription s : subscriptions) {
                        subs.add(s.getPlan().getTitle());
                    }
                    CharSequence[] s = subs.toArray(new CharSequence[subs.size()]);
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Play livestream?");
                    builder.setItems(s,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String stream = subscriptions.get(which).getStreamUrl();
                                    Log.d("LIVE", stream);
                                    Video live = new Video();
                                    live.setVidUrl(stream);
                                    Intent intent = new Intent(getActivity(), PlaybackActivity.class);
                                    intent.putExtra(DetailsActivity.Video, (Serializable) live);
                                    startActivity(intent);
                                }
                            });
                    builder.create().show();
                }
            }
        }
    }

    private void getSelectVid(final Presenter.ViewHolder itemViewHolder, final Object item) {
        String cookies = "__cfduid=" + cfduid + "; sails.sid=" + sailssid;
        final Video video = (Video) item;
        String uri = "https://www.floatplane.com/api/video/url?guid=" + video.getGuid() + "&quality=1080";
        RequestTask rt = new RequestTask(getActivity().getApplicationContext());
        rt.sendRequest(uri, cookies, new RequestTask.VolleyCallback() {
            @Override
            public void onSuccess(String string) {
                Video vid = video;
                //vid.setGuid(string.replaceAll("\"", ""));
                vid.setVidUrl(string.replaceAll("\"", ""));

                Log.d(TAG, "Item: " + vid.toString());
                Intent intent = new Intent(getActivity(), DetailsActivity.class);
                intent.putExtra(DetailsActivity.Video, vid);

                Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        getActivity(),
                        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                        DetailsActivity.SHARED_ELEMENT_NAME)
                        .toBundle();
                getActivity().startActivity(intent, bundle);
            }

            @Override
            public void onSuccessCreator(String string, String creatorGUID) {
            }

            @Override
            public void onError(VolleyError error) {
            }
        });
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(
                Presenter.ViewHolder itemViewHolder,
                Object item,
                RowPresenter.ViewHolder rowViewHolder,
                Row row) {
            if (item instanceof Video) {
                mBackgroundUri = ((Video) item).getThumbnail().getPath();
                startBackgroundTimer();
                final ListRow listRow = (ListRow) row;
                final ArrayObjectAdapter current = (ArrayObjectAdapter) listRow.getAdapter();
                int selected = current.indexOf(item);
                colSelected = selected;
                for (Subscription s : subscriptions) {
                    if (((Video) item).getCreator().equals(s.getCreator())) {
                        rowSelected = subscriptions.indexOf(s);
                    }
                }
                if (selected != -1 && (current.size() - 1) == selected) {
                    for (Subscription sub : subscriptions) {
                        client.getVideos(sub.getCreator(), page + 1, videos -> {
                            gotVideos(sub.getCreator(), videos);
                            return Unit.INSTANCE;
                        });
                    }
                    page++;
                }
            }
        }
    }

    private class UpdateBackgroundTask extends TimerTask {

        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateBackground(mBackgroundUri);
                }
            });
        }
    }

    private class GridItemPresenter extends Presenter {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            TextView view = new TextView(parent.getContext());
            view.setLayoutParams(new ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT));
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
            view.setBackgroundColor(
                    ContextCompat.getColor(getContext(), R.color.default_background));
            view.setTextColor(Color.WHITE);
            view.setGravity(Gravity.CENTER);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            ((TextView) viewHolder.view).setText((String) item);
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
        }
    }
}