package free.mtutunik.mygooglemap

import android.app.IntentService
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaPlayer.OnPreparedListener
import android.os.IBinder
import java.util.concurrent.ArrayBlockingQueue

/**
 * Created by mtutunik on 5/23/18.
 */
class PlayerService : IntentService("PlayerService"), OnPreparedListener {

    val PLAY_ACTION = "PLAY_TOUR"
    val TOUR_NAME_KEY = "TOUR_NAME"

    var mMediaPlayer: MediaPlayer? = null

    lateinit var mPlayQueue : ArrayBlockingQueue<String>


    init {
        mPlayQueue = ArrayBlockingQueue<String>(10)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)

        if (intent?.action == PLAY_ACTION) {
            val fname = intent?.getStringExtra(TOUR_NAME_KEY)
            mMediaPlayer = MediaPlayer()
            mMediaPlayer?.setDataSource(fname)
            mMediaPlayer?.setOnPreparedListener(this)
            mMediaPlayer?.prepareAsync()
        }
    }

    override fun onHandleIntent(intent: Intent?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    override fun onPrepared(mp: MediaPlayer?) {
        mMediaPlayer?.start()
    }




}