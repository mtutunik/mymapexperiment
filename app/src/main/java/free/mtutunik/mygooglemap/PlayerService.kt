package free.mtutunik.mygooglemap

import android.app.IntentService
import android.app.Service
import android.content.Intent
import android.location.Location
import android.media.MediaPlayer
import android.media.MediaPlayer.OnPreparedListener
import android.media.MediaRecorder
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import free.mtutunik.mygooglemap.PlayerService.Companion.PLAY_ACTION
import free.mtutunik.mygooglemap.PlayerService.Companion.RECORD_ACTION
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import kotlin.concurrent.fixedRateTimer

/**
 * Created by mtutunik on 5/23/18.
 */
class PlayerService : Service(), OnPreparedListener {
    companion object {
        public const val PLAY_ACTION = "PLAY_TOUR"
        public const val RECORD_ACTION = "RECORD_TOUR"
        private const val TOUR_RECORD_SAVING_PERIOD = 15000L
    }
    val TOUR_NAME_KEY = "TOUR_NAME"

    var mMediaPlayer: MediaPlayer? = null
    private var mMediaRecorder: MediaRecorder? = null
    private lateinit var mHandler : Handler
    private lateinit var mHandlerThread: HandlerThread
    private var mPeriodicSaveTimer: Timer? = null


    lateinit var mPlayQueue : ArrayBlockingQueue<String>
    public lateinit var mLastLocation: Location


    init {
        mPlayQueue = ArrayBlockingQueue<String>(10)
        mHandlerThread = HandlerThread("PlyerThread")
        mHandlerThread.start()
        mHandler = Handler(mHandlerThread.looper)
    }


    private val mBinder = PlayerBinder()
    inner class PlayerBinder : Binder() {
        fun getService() : PlayerService {
            return this@PlayerService
        }
    }

    public var mDbHelper: DbHelper? = null

    override fun onBind(intent: Intent?): IBinder {
        return mBinder
    }



    public fun recordAudio(fname: String) {
        mHandler.post {

            try {
                mMediaRecorder = MediaRecorder()
                mMediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
                mMediaRecorder?.setOutputFormat(
                        MediaRecorder.OutputFormat.AAC_ADTS)
                mMediaRecorder?.setOutputFile(fname)
                mMediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mMediaRecorder?.prepare()

                mMediaRecorder?.start()
                mDbHelper?.addTourPart(mLastLocation, fname, System.currentTimeMillis())
                mPeriodicSaveTimer = fixedRateTimer(period = TOUR_RECORD_SAVING_PERIOD) {
                    mDbHelper?.addTourPart(mLastLocation, fname, System.currentTimeMillis())
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    public fun playAudio(fname : String) {
        mHandler.post {
            mMediaPlayer = MediaPlayer()
            mMediaPlayer?.setDataSource(fname)
            mMediaPlayer?.prepare()
            mMediaPlayer?.start()
        }
    }


    public fun stopRecordingAudio() {
        mHandler.post {
            mMediaRecorder?.stop()
            mMediaRecorder?.release()
            mMediaRecorder = null
        }
    }


    public fun stopPlayingAudio() {
        mHandler.post {
            mMediaPlayer?.release()
            mMediaPlayer = null
        }
    }



    override fun onPrepared(mp: MediaPlayer?) {
        mMediaPlayer?.start()
    }




}