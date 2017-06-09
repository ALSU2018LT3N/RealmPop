package realm.io.realmpop.ui.gameroom

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.widget.DividerItemDecoration
import kotlinx.android.synthetic.main.activity_gameroom.*
import org.jetbrains.anko.alert
import realm.io.realmpop.R
import realm.io.realmpop.ui.BaseAuthenticatedActivity
import realm.io.realmpop.ui.game.GameActivity
import realm.io.realmpop.databinding.ActivityGameroomBinding

class GameRoomActivity : BaseAuthenticatedActivity() {

    private val availableHeartbeat = AvailableHeartbeat()
    private val viewModel by lazy { ViewModelProviders.of(this).get(GameRoomViewModel::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DataBindingUtil.setContentView<ActivityGameroomBinding>(this, R.layout.activity_gameroom)
        binding.vm = viewModel
        lifecycle.addObserver(availableHeartbeat)

        player_list.adapter = PlayerRecyclerViewAdapter(viewModel)
        player_list.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        bindState()
    }

    fun bindState() {
        viewModel.state.observe(this, Observer { state ->
              when(state) {
                  GameRoomViewModel.State.VIEWING -> availableHeartbeat.start()
                  GameRoomViewModel.State.CHALLENGING -> availableHeartbeat.stop()
                  GameRoomViewModel.State.CHALLENGED -> presentChallengeDialog()
                  GameRoomViewModel.State.STARTING_GAME -> startActivityForResult(Intent(this, GameActivity::class.java), 0)
                  GameRoomViewModel.State.APP_RESTART_NEEDED -> restartApp()
              }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        viewModel.gameFinished()
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun presentChallengeDialog() {
        availableHeartbeat.stop()
        alert(getString(R.string.game_challenge_proposition, viewModel.challengerName)) {
            theme.applyStyle(R.style.AppTheme_RealmPopDialog, true)
            positiveButton(getString(R.string.game_challenge_accept)) { viewModel.acceptChallenge() }
            negativeButton(getString(R.string.game_challenge_decline)) { viewModel.declineChallenge() }
        }.show()
    }

}