package realm.io.realmpop.view;

import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.MainThread;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.joda.time.Interval;
import org.joda.time.Period;

import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.realm.ObjectChangeSet;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmObject;
import io.realm.RealmObjectChangeListener;
import realm.io.realmpop.R;
import realm.io.realmpop.model.Game;
import realm.io.realmpop.model.Player;
import realm.io.realmpop.model.Score;
import realm.io.realmpop.model.Side;
import realm.io.realmpop.util.GameHelpers;

import static realm.io.realmpop.util.RandomNumberUtils.generateNumber;

public class GameActivity extends BaseAuthenticatedActivity {

    @BindView(R.id.playerLabel1) public TextView player1;
    @BindView(R.id.playerLabel2) public TextView player2;
    @BindView(R.id.message)      public TextView message;
    @BindView(R.id.timer)        public TextView timerLabel;
    @BindView(R.id.bubbleBoard)  public RelativeLayout bubbleBoard;

    private Game challenge;
    private Side mySide;
    private Side otherSide;
    private Player me;

    private GameTimer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        ButterKnife.bind(this);
        message.setText("");
        timer = new GameTimer(1);

        me = Player.byId(getRealm(), getPlayerId());
        challenge = me.getCurrentGame();
        mySide = challenge.getPlayer1().getPlayerId().equals(me.getId()) ? challenge.getPlayer1() : challenge.getPlayer2();
        otherSide = challenge.getPlayer1().getPlayerId().equals(me.getId()) ? challenge.getPlayer2() : challenge.getPlayer1();

        setupBubbleBoard();
    }

    @Override
    protected void onResume() {
        super.onResume();

        me.addChangeListener(MeChangeListener);
        challenge.addChangeListener(GameChangeListener);
        mySide.addChangeListener(SideChangeListener);
        otherSide.addChangeListener(SideChangeListener);

        timer.startTimer(new GameTimer.TimerDelegate() {
            @Override
            public void onTimerUpdate(final GameTimer.TimerEvent timerEvent) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        timerLabel.setText(timerEvent.timeElapsedString);
                    }
                });
            }

            @Override
            public void onTimeExpired(GameTimer.TimeExpiredEvent timeExpiredEvent) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        timerLabel.setText("60.0");
                        update();
                    }
                });
            }
        });

        update();
    }

    @Override
    protected void onPause() {
        super.onPause();
        timer.stopTimer();

        if(me != null) { me.removeChangeListener(MeChangeListener); }
        if(challenge != null) { me.removeChangeListener(GameChangeListener); }
        if(mySide != null) { me.removeChangeListener(SideChangeListener); }
        if(otherSide != null) { me.removeChangeListener(SideChangeListener); }


    }

    @Override
    public void onBackPressed() {
        exitGameAfterDelay(0);
    }

    public void exitGameAfterDelay(final int delay) {
        Handler handler = new Handler(getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Player.assignAvailability(true, mySide.getPlayerId());
                Player.assignAvailability(true, otherSide.getPlayerId());
            }
        }, delay);
    }

    public void onBubbleTap(final long numberTapped) {

        // Just to make sure, if there are none left to tap, exit.
        if (mySide.getLeft() <= 0) {
            return;
        }

        final int bubble = challenge.getNumberArray()[(int)mySide.getLeft() - 1];
        final long currLeft = mySide.getLeft();
        final String mySidePlayerId = mySide.getPlayerId();

        if(bubble == numberTapped) {
            getRealm().executeTransactionAsync(new Realm.Transaction() {
                @Override
                public void execute(Realm bgRealm) {
                    Game currentGame = GameHelpers.playerWithId(mySidePlayerId, bgRealm).getCurrentGame();
                    Side mySide = currentGame.sideWithPlayerId(mySidePlayerId);
                    mySide.setLeft(currLeft - 1);
                }
            });

        } else {

            getRealm().executeTransactionAsync(new Realm.Transaction() {
                @Override
                public void execute(Realm bgRealm) {
                    Game currentGame = GameHelpers.playerWithId(mySidePlayerId, bgRealm).getCurrentGame();
                    Side mySide = currentGame.sideWithPlayerId(mySidePlayerId);
                    mySide.setFailed(true);
                }

            }, new Realm.Transaction.OnSuccess() {
                @Override
                public void onSuccess() {
                    message.setText("You tapped " + numberTapped + " instead of " + bubble);
                    message.setVisibility(View.VISIBLE);
                }
            });
        }
    }

        private void setupBubbleBoard() {

        Resources res = getResources();
        DisplayMetrics display = res.getDisplayMetrics();
        final int spaceTakenByButton = res.getDimensionPixelSize(R.dimen.bubble_button_diameter);
        final int titleBarHeight = res.getDimensionPixelSize(res.getIdentifier("status_bar_height", "dimen", "android"));

        final int MAX_X_MARGIN = display.widthPixels - spaceTakenByButton; // bubble button diameter;

        final int MAX_Y_MARGIN =  display.heightPixels - (res.getDimensionPixelSize(R.dimen.activity_vertical_margin) //top margin
                                                        + res.getDimensionPixelSize(R.dimen.activity_vertical_margin) // bottom margin
                                                        + res.getDimensionPixelSize(R.dimen.realm_pop_status_bar_height) // pop status bar height
                                                        + titleBarHeight // android status bar height
                                                        + spaceTakenByButton); // bubble button diameter;

        for(final int bubbleNumber : challenge.getNumberArray()) {
            View bubbleView = getLayoutInflater().inflate(R.layout.bubble, bubbleBoard, false);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) bubbleView.getLayoutParams();

            params.leftMargin = generateNumber(0, MAX_X_MARGIN);
            params.topMargin = generateNumber(0, MAX_Y_MARGIN);

            ((TextView) bubbleView.findViewById(R.id.bubbleValue)).setText(String.valueOf(bubbleNumber));
            bubbleView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bubbleBoard.removeView(v);
                    onBubbleTap(bubbleNumber);
                }
            });

            bubbleBoard.addView(bubbleView, params);
        }
    }

    private void update() {

        if(me.getCurrentGame() == null) {
            return;
        }

        final String mySidePlayerId = mySide.getPlayerId();
        final String otherSidePlayerId = otherSide.getPlayerId();

        getRealm().executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm bgRealm) {

                Game currentGame = GameHelpers.playerWithId(mySidePlayerId, bgRealm).getCurrentGame();
                Side mySide = currentGame.sideWithPlayerId(mySidePlayerId);
                Side otherSide = currentGame.sideWithPlayerId(otherSidePlayerId);

                if (mySide.getLeft() == 0) {

                    // MySide finished but time hasn't been recorded yet, so let's do that.
                    if (mySide.getTime() == 0) {
                        mySide.setTime(Double.valueOf(timerLabel.getText().toString()));
                    }

                    // Both side times have been recorded, the game is over.
                    if (otherSide.getTime() > 0 && mySide.getTime() > 0) {
                        if (otherSide.getTime() < mySide.getTime()) {
                            mySide.setFailed(true);
                        } else {
                            otherSide.setFailed(true);
                        }
                    }
                }
            }

        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {

                player1.setText(challenge.getPlayer1().getName() + " : " + challenge.getPlayer1().getLeft());
                player2.setText(challenge.getPlayer2().getName() + " : " + challenge.getPlayer2().getLeft());

                if(challenge.isGameOver()) {
                    timer.stopTimer();
                    if (otherSide.isFailed()) {
                        showMessage("You win! Sweet");
                    } else if (mySide.isFailed()) {
                        if (TextUtils.isEmpty(message.getText())) {
                            message.setText("You lost!");
                        }
                        message.setVisibility(View.VISIBLE);
                    }
                    exitGameAfterDelay(5000);

//                } else if(timeHasExpired()) {
//                    // If time is expired and I have not got a time yet, I'm out of time.
//                    if (mySide.getTime() == 0) {
//                        showMessage("You're out of time!");
//                        exitGameAfterDelay(5000);
//
//                    // Time expired and I've got time then I must have one.
//                    } else {
//                        showMessage("You win! Sweet");
//                        exitGameAfterDelay(5000);
//                    }
//                }
                }
               }
        });
    }

    @MainThread
    private void showMessage(String text) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }

    private RealmObjectChangeListener<Game> GameChangeListener = new RealmObjectChangeListener<Game>() {
        @Override
        public void onChange(Game game, ObjectChangeSet objectChangeSet) {
            if (objectChangeSet.isDeleted() || !game.isValid()) {
                //                finish(); // TODO: Go back to finish() instead of a specific Activity when https://github.com/realm/realm-java/issues/4502 gets resolved.
                goTo(GameRoomActivity.class);

            }
        }
    };

    private RealmObjectChangeListener<Side> SideChangeListener = new RealmObjectChangeListener<Side>() {
        @MainThread
        @Override
        public void onChange(Side side, ObjectChangeSet objectChangeSet) {
            if(objectChangeSet.isDeleted() || !side.isValid()) {
                //                finish(); // TODO: Go back to finish() instead of a specific Activity when https://github.com/realm/realm-java/issues/4502 gets resolved.
                goTo(GameRoomActivity.class);

            }
            if(challenge.isGameOver()) {
                mySide.removeAllChangeListeners();
                otherSide.removeAllChangeListeners();
            }
            update();
        }
    };

    private RealmObjectChangeListener<Player> MeChangeListener = new RealmObjectChangeListener<Player>() {
        @MainThread
        @Override
        public void onChange(Player player, ObjectChangeSet objectChangeSet) {
            if(objectChangeSet.isDeleted() || !player.isValid()) {
                //                finish(); // TODO: Go back to finish() instead of a specific Activity when https://github.com/realm/realm-java/issues/4502 gets resolved.
                goTo(SplashActivity.class);
            } else if(me.getCurrentGame() == null) {
                goTo(GameRoomActivity.class);
            }
        }
    };

}
