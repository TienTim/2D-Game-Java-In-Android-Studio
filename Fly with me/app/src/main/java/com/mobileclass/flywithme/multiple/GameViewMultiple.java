package com.mobileclass.flywithme.multiple;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.mobileclass.flywithme.GameActivityMultiple;
import com.mobileclass.flywithme.R;
import com.mobileclass.flywithme.models.Post;
import com.mobileclass.flywithme.models.User;
import com.mobileclass.flywithme.utils.Singleton;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class GameViewMultiple extends SurfaceView implements Runnable {

    private static final String TAG = "NewPost";
    private static final String TAG_GET = "GetPost";
    private Thread thread;
    private boolean isPlaying, isGameOver = false, isExit = false, pressExit = false;
    private int screenX, screenY;
    public static float screenRatioX, screenRatioY, screenRatio;
    private Paint paint, paintLeft, paintRight;
    private SharedPreferences prefs;
    private SoundPool soundPool;
    private List<BulletMultiple> bulletsLeft, bulletsRight;
    private int sound;
    private FlightMultiple flightLeft, flightRight;
    private GameActivityMultiple activity;
    private BackgroundMultiple background1;

    private DatabaseReference mDatabase;
    private DatabaseReference mPostReference, usersDataReference;
    Singleton singleton = Singleton.getInstance();
    final String userId = getUid();
    final String databaseChild = "user-posts";
    final boolean isServer = Objects.equals(singleton.left, userId);
    private boolean leftState = true, rightState = true, triedLoad = false;
    Set<Long> playTimes = new HashSet<Long>();
    boolean shootFlag = true, leftFlag = true, rightFlag = true, isReady = false;
    MediaPlayer mediaPlayer;
    long globalTime;
    private Integer win, lost;

    public GameViewMultiple(GameActivityMultiple activity, int screenX, int screenY) {
        super(activity);

        this.activity = activity;
        Date date = new Date();
        globalTime = date.getTime() - 5000;

        prefs = activity.getSharedPreferences("game", Context.MODE_PRIVATE);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_GAME)
                .build();
        soundPool = new SoundPool.Builder()
                .setAudioAttributes(audioAttributes)
                .build();
        sound = soundPool.load(activity, R.raw.shoot, 1);

        this.screenX = screenX;
        this.screenY = screenY;
        screenRatioX = 1920f / screenX;
        screenRatioY = 1080f / screenY;
        screenRatio = screenRatioY / screenRatioX;

        background1 = new BackgroundMultiple(screenX, screenY, getResources());

        flightLeft = new FlightMultiple(this, screenX, screenY, getResources(), true);
        flightRight = new FlightMultiple(this, screenX, screenY, getResources(), false);

        bulletsLeft = new ArrayList<>();
        bulletsRight = new ArrayList<>();

        paint = new Paint();
        paint.setTextSize(128);
        paint.setColor(Color.BLACK);
        paintLeft = new Paint();
        paintLeft.setTextSize(60);
        paintLeft.setColor(Color.parseColor("#3B731E"));
        paintRight = new Paint();
        paintRight.setTextSize(60);
        paintRight.setColor(Color.RED);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        if (!isServer)
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    composePostPolymorphism(0, 0, false, false,
                    false, false, false, true);
                }
            }, 1000);

        mPostReference = FirebaseDatabase.getInstance().getReference().child(databaseChild);
        addPostEventListener(mPostReference);
        usersDataReference = FirebaseDatabase.getInstance().getReference().child("users-data");
        usersDataReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (triedLoad)
                    return;
                win = dataSnapshot.child(singleton.username).child("win").getValue(Integer.class);
                lost = dataSnapshot.child(singleton.username).child("lost").getValue(Integer.class);
                triedLoad = true;
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }


    private void addPostEventListener(DatabaseReference mPostReference) {
        ValueEventListener postListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (isGameOver || isExit)
                    return;
                Map<String, Map<String, Map<String, ?>>> postMap =
                        (HashMap<String, Map<String, Map<String, ?>>>) dataSnapshot.getValue();
                for (String user : postMap.keySet()) {
                    boolean isLeftSignal = Objects.equals(user, singleton.left);
                    if (!isLeftSignal && !Objects.equals(user, singleton.right))
                        continue;
                    Map<String, Map<String, ?>> datumMap = postMap.get(user);
                    Date date = new Date();
                    long currentTime = date.getTime() - 5000;
                    globalTime = Math.max(globalTime, currentTime);
                    for (String key : datumMap.keySet()) {
                        if (currentTime < globalTime)
                            return;
                        Map<String, ?> dataMap = datumMap.get(key);
                        long time = (long) dataMap.get("time");
                        if (time < currentTime || playTimes.contains(time))
                            continue;
                        playTimes.add(time);
                        if (isLeftSignal) {
                            singleton.scoreLeft = Math.max(singleton.scoreLeft,
                                    (long) dataMap.get("scoreLeft"));
                            singleton.scoreRight = Math.max(singleton.scoreRight,
                                    (long) dataMap.get("scoreRight"));
                            flightLeft.isGoingUp = (boolean) dataMap.get("bound");
                            flightLeft.toShoot += (boolean) dataMap.get("shoot") ? 1 : 0;
                            leftState = (boolean) dataMap.get("left");
                            rightState = (boolean) dataMap.get("right");
                            if ((boolean) dataMap.get("end"))
                                if (singleton.scoreLeft < 5 && singleton.scoreRight < 5)
                                    isExit = true;
                                else
                                    isGameOver = true;
                        } else {
                            if ((boolean) dataMap.get("ready"))
                                isReady = true;
                            flightRight.isGoingUp = (boolean) dataMap.get("bound");
                            flightRight.toShoot += (boolean) dataMap.get("shoot") ? 1 : 0;
                            isExit = (boolean) dataMap.get("end");
                        }
                    }
                }
                Log.w(TAG_GET, databaseChild);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG_GET, "loadPost:onCancelled", databaseError.toException());
            }
        };
        mPostReference.addValueEventListener(postListener);
    }


    @Override
    public void run() {
        if (!prefs.getBoolean("isMute", false)) {
            mediaPlayer = MediaPlayer.create(activity, R.raw.go_up);
            mediaPlayer.start();
            mediaPlayer.setLooping(true);
        }
        while (isPlaying) {
            update ();
            draw ();
            sleep ();
        }
    }

    private void update () {

        if (flightLeft.isGoingUp)
            flightLeft.y -= 30 * screenRatioY;
        else
            flightLeft.y += 30 * screenRatioY;

        flightLeft.y = Math.max(130, flightLeft.y);
        flightLeft.y = Math.min(screenY - flightLeft.height, flightLeft.y);

        flightRight.y += 30 * screenRatioY * (flightRight.isGoingUp ? -1 : 1);
        flightRight.y = Math.max(130, flightRight.y);
        flightRight.y = Math.min(screenY - flightRight.height, flightRight.y);

        List<BulletMultiple> trash = new ArrayList<>();

        for (BulletMultiple bullet : bulletsLeft) {
            if (bullet.x > screenX)
                trash.add(bullet);
            bullet.x += 40 * screenRatioX * screenRatio;
            if (isServer && Rect.intersects(flightRight.getCollisionShape(),
                    bullet.getCollisionShape()) && rightFlag) {
                rightFlag = false;
                composePost(singleton.scoreLeft + 1, singleton.scoreRight, false,
                        false, true, false, singleton.scoreLeft > 3);
                activity.runOnUiThread(() -> {
                    new CountDownTimer(1000, 1000) {
                        @Override
                        public void onFinish() {
                            rightFlag = true;
                        }
                        public void onTick(long millisUntilFinished) {
                        }
                    }.start();
                });
            }
        }

        for (BulletMultiple bullet : bulletsRight) {
            if (bullet.x < 0)
                trash.add(bullet);
            bullet.x -= 40 * screenRatioX * screenRatio;
            if (isServer && Rect.intersects(flightLeft.getCollisionShape(),
                    bullet.getCollisionShape()) && leftFlag) {
                leftFlag = false;
                composePost(singleton.scoreLeft, singleton.scoreRight + 1, false,
                        false, false, true, singleton.scoreRight > 3);
                activity.runOnUiThread(() -> {
                    new CountDownTimer(1000, 1000) {
                        @Override
                        public void onFinish() {
                            leftFlag = true;
                        }
                        public void onTick(long millisUntilFinished) {
                        }
                    }.start();
                });
            }
        }

        for (BulletMultiple bullet : trash) {
            bulletsLeft.remove(bullet);
            bulletsRight.remove(bullet);
        }
    }

    private void draw () {

        if (getHolder().getSurface().isValid()) {

            Canvas canvas = getHolder().lockCanvas();
            canvas.drawBitmap(background1.background, background1.x, background1.y, paint);

            Bitmap sound = BitmapFactory.decodeResource(getResources(),
                    prefs.getBoolean("isMute", false) ? R.drawable.volume_off :
                            R.drawable.volume_on);
            sound = Bitmap.createScaledBitmap(sound, 60, 60, false);
            canvas.drawBitmap(sound, screenX - 160, 30, paint);

            canvas.drawText("Exit", 30, 70, pressExit ? paintRight : paintLeft);
            canvas.drawText(singleton.scoreLeft + " - " + singleton.scoreRight,
                    screenX / 2f - 164, 164, paint);
            canvas.drawText(singleton.leftName, flightLeft.x, flightLeft.y - 20, paintLeft);
            canvas.drawText(singleton.rightName, flightRight.x, flightRight.y - 20, paintRight);

            canvas.drawBitmap(leftState ? flightLeft.getFlight(true) : flightLeft.getDead(),
                        flightLeft.x, flightLeft.y, paint);
            leftState = true;
            canvas.drawBitmap(rightState ? flightRight.getFlight(false) : flightRight.getDead(),
                        flightRight.x, flightRight.y, paint);
            rightState = true;
            if (isExit || isGameOver || pressExit) {
                String m = (isExit || pressExit) ? "Player exits" :
                        ("You " + ((isServer && singleton.scoreLeft > singleton.scoreRight) ||
                                (!isServer && singleton.scoreLeft < singleton.scoreRight) ? "win" : "lose"));
                canvas.drawText(m, screenX / 2f - 300, screenY / 2f, paint);
                singleton.message = m + ". Choose partner to play.";
                isPlaying = false;
                if (isGameOver) {
                    if ((isServer && singleton.scoreLeft > singleton.scoreRight) ||
                            (!isServer && singleton.scoreLeft < singleton.scoreRight))
                        usersDataReference.child(singleton.username).child("win").setValue(win == null ? 1 : win + 1);
                    else
                        usersDataReference.child(singleton.username).child("lost").setValue(lost == null ? 1 : lost + 1);
                }
                getHolder().unlockCanvasAndPost(canvas);
                waitBeforeExiting();
                return;
            }

            for (BulletMultiple bullet : bulletsLeft)
                canvas.drawBitmap(bullet.bullet, bullet.x, bullet.y, paint);
            for (BulletMultiple bullet : bulletsRight)
                canvas.drawBitmap(bullet.bullet, bullet.x, bullet.y, paint);
            if (!isReady) {
                Bitmap ready = BitmapFactory.decodeResource(getResources(), R.drawable.ready);
                int width = (int) (ready.getWidth() / 4  * GameViewMultiple.screenRatioX);
                int height = (int) (ready.getHeight() / 4 * GameViewMultiple.screenRatioY);
                ready = Bitmap.createScaledBitmap(ready, width, height, false);
                canvas.drawBitmap(ready, screenX / 3f, screenY / 3f, paint);
            }

            getHolder().unlockCanvasAndPost(canvas);

        }

    }

    private void waitBeforeExiting() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }
            Thread.sleep(3000);
            activity.finish();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void sleep () {
        try {
            Thread.sleep(17);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void resume () {
        isPlaying = true;
        thread = new Thread(this);
        thread.start();
    }

    public void pause () {
        try {
            isPlaying = false;
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isReady)
            return false;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (event.getY() < 150) {
                    if (event.getX() < 300) {
                        composePost(singleton.scoreLeft, singleton.scoreRight, false,
                                false, true, true, true);
                        pressExit = true;
                    } else if (event.getX() > screenX - 300) {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("isMute", !prefs.getBoolean("isMute", false));
                        editor.apply();
                        if (!prefs.getBoolean("isMute", false)) {
                            mediaPlayer = MediaPlayer.create(activity, R.raw.go_up);
                            mediaPlayer.start();
                            mediaPlayer.setLooping(true);
                        } else
                            if (mediaPlayer != null)
                                mediaPlayer.stop();
                    }
                    break;
                }
                boolean isShoot = (event.getX() < screenX / 2f) != isServer;
                if (!isShoot)
                    composePost(singleton.scoreLeft, singleton.scoreRight, true, false,
                            true, true, false);
                else if (shootFlag) {
                    shootFlag = false;
                    new CountDownTimer(700, 700) {
                        @Override
                        public void onFinish() {
                            shootFlag = true;
                        }
                        public void onTick(long millisUntilFinished) {
                        }
                    }.start();
                    composePost(singleton.scoreLeft, singleton.scoreRight, false, true,
                            true, true, false);
                }
                break;
            case MotionEvent.ACTION_UP:
                composePost(singleton.scoreLeft, singleton.scoreRight, false, false,
                        true, true, false);
                break;
        }
        return true;
    }

    public String getUid() {
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    private void writeNewPost(String username, long scoreLeft, long scoreRight, boolean bound,
                              boolean shoot, boolean left, boolean right, boolean end, boolean ready) {
        String key = mDatabase.child("posts").push().getKey();
        Date date = new Date();
        Post post = new Post(userId, username, scoreLeft, scoreRight, bound, shoot, left, right,
                end, date.getTime(), ready);
        Map<String, Object> postValues = post.toMap();

        Map<String, Object> childUpdates = new HashMap<>();
        childUpdates.put("/" + databaseChild + "/" + userId + "/" + key, postValues);

        mDatabase.updateChildren(childUpdates);
        Log.w(TAG, databaseChild);
    }

    public void newBullet(boolean isLeft) {
        if (!prefs.getBoolean("isMute", false))
            soundPool.play(sound, 1, 1, 0, 0, 1);
        BulletMultiple bullet = new BulletMultiple(getResources(), isLeft);
        bullet.x = isLeft ? flightLeft.x + flightLeft.width : flightRight.x;
        bullet.y = isLeft ? flightLeft.y + (flightLeft.height / 2) :
                flightRight.y + (flightRight.height / 2) + 25;
        if (isLeft)
            bulletsLeft.add(bullet);
        else
            bulletsRight.add(bullet);
    }

    public void composePost(long scoreLeft, long scoreRight, boolean bound, boolean shoot,
                            boolean left, boolean right, boolean end) {
        composePostPolymorphism(scoreLeft, scoreRight, bound, shoot, left, right, end, false);
    }

    public void composePostPolymorphism(long scoreLeft, long scoreRight, boolean bound,
                                        boolean shoot, boolean left, boolean right,
                                        boolean end, boolean ready) {
        mDatabase.child("users").child(userId).addListenerForSingleValueEvent(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        User user = dataSnapshot.getValue(User.class);
                        if (user == null) {
                            Log.e(TAG, "User " + userId + " is unexpectedly null");
                            Toast.makeText(activity, "Error: could not fetch user.",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            writeNewPost(user.username, scoreLeft, scoreRight, bound, shoot,
                                    left, right, end, ready);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.w(TAG, "getUser:onCancelled", databaseError.toException());
                    }
                });
    }
}
