package free.mtutunik.mygooglemap

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by mtutunik on 6/10/18.
 */

class DbHelper(context: Context) : SQLiteOpenHelper(context, "tours.db", null, 1) {


    val TOUR_MAP_TABLE = "tourmap"
    val TOURS_TABLE = "tours"


    companion object {
        public val ID: String = "_id"
        public val TOUR_ID: String = "TOURID"
        public val ORDER_NUMBER: String = "ORDER_NUMBER"
        public val RECORD_TIME: String = "RECORD_TIME"
        public val AUDIO_NAME: String = "AUDIONAME"
        public val LAT: String = "LAT"
        public val LON: String = "LON"

        public val TOUR_NAME: String = "TOUR_NAME"

        private var _uniqueID = AtomicInteger(0)
        public val uniqueID : Int
            get() {
                return _uniqueID.get()
            }

        private var mActiveTourId: Int = -1
    }

    val CREATE_TOURS_TABLE = "CREATE TABLE if not exists " + TOURS_TABLE + " (" +
            "${ID} integer PRIMARY KEY autoincrement," +
            "${TOUR_ID} integer," +
            "${TOUR_NAME} text"+
            ")"

    val CREATE_TOUR_MAP_TABLE = "CREATE TABLE if not exists ${TOUR_MAP_TABLE} (" +
            "${ID} integer PRIMARY KEY autoincrement," +
            "${TOUR_ID} integer," +
            "${ORDER_NUMBER} integer," +
            "${RECORD_TIME} integer," +
            "${AUDIO_NAME} text," +
            "${LAT} real," +
            "${LON} real" +
            ")"


    public val toursCount : Long
        get() {
            val db = writableDatabase
            return DatabaseUtils.queryNumEntries(db, TOURS_TABLE)
        }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(CREATE_TOURS_TABLE)
        db?.execSQL(CREATE_TOUR_MAP_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun createNewTour(name: String) {
        val tourName = "${name}${toursCount + 1}"
        var contentVals = ContentValues()
        contentVals.put(DbHelper.TOUR_NAME, tourName)
        setActiveTourId(newID())
        contentVals.put(DbHelper.TOUR_ID, mActiveTourId)
        val db = writableDatabase
        db.insert(TOURS_TABLE, null, contentVals)
    }

    fun newID() : Int {
        return _uniqueID.incrementAndGet()
    }

    fun setActiveTourId(tourId: Int) {
        mActiveTourId = tourId
    }


    fun addTourPart(location: Location, audioFname: String?, recordingTime: Long) {
        var contentVals = ContentValues()
        contentVals.put(LAT, location.latitude)
        contentVals.put(LON, location.longitude)
        contentVals.put(AUDIO_NAME, audioFname)
        contentVals.put(RECORD_TIME, recordingTime)
        contentVals.put(TOUR_ID, mActiveTourId)
        val db = writableDatabase
        db.insert(TOUR_MAP_TABLE, null, contentVals)
    }
}