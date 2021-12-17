//  Created by Maciej Pomykala on 27.06.2021
//  Copyright © 2021 Blef Team.

package app.blef.blef

import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.MutableLiveData
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.concurrent.fixedRateTimer

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        resources.displayMetrics.density = (resources.displayMetrics.widthPixels / 360).toFloat()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mHandler = Handler(Looper.getMainLooper())
        val message = MutableLiveData<String>()

        val pixelDensity = resources.displayMetrics.density
        fun adjustForDensity(raw: Int): Int {
            return((raw * pixelDensity).toInt())
        }

        val redirectReason = intent.getIntExtra("reason", -1)
        if (redirectReason != -1) {
            val popupMessage = when (redirectReason) {
                redirectReasons.INVALID_UUID -> getString(R.string.invalid_uuid)
                redirectReasons.ENGINE_DOWN -> getString(R.string.engine_down)
                redirectReasons.GAME_UNAVAILABLE -> getString(R.string.game_unavailable)
                else -> getString(R.string.something_went_wrong)
            }
            AlertDialog.Builder(this)
                .setMessage(popupMessage)
                .setPositiveButton("OK", null)
                .show()
        }

        val pg = findViewById<LinearLayout>(R.id.publicGames)
        val headerText = TextView(this@MainActivity)
        headerText.text = getString(R.string.public_games)
        val playerFilter = EditText(this@MainActivity)
        playerFilter.height = adjustForDensity(60)
        playerFilter.hint = getString(R.string.filter_public_games)
        playerFilter.tag = "player_filter"
        pg.addView(headerText)
        pg.addView(playerFilter)

        fixedRateTimer("update_public_games", false, 0L, 1000) {
            if (hasWindowFocus()) {
                queryEngine(
                    R.id.activity_main,
                    baseUrl
                ) { response ->
                    val newMessage = response.body!!.string()
                    if (newMessage != message.value.toString()) {
                        mHandler.post{
                            message.setValue(newMessage)
                        }
                    }
                }
            }
        }

        findViewById<Button>(R.id.create).setOnClickListener {
            val intent = Intent(this, Creating::class.java)
            startActivity(intent)
        }

        fun generatePublicGames() {
            val unfilteredPublicGames = JSONArray(message.value.toString())
            var filteredPublicGames = JSONArray()
            val filter = pg.findViewWithTag<EditText>("player_filter")
            if (filter != null) {
                for (i in 0 until unfilteredPublicGames.length()) {
                    var isMatch = false
                    val iGame = unfilteredPublicGames.getJSONObject(i)
                    if (iGame.getString("room").contains(filter.text.toString(), ignoreCase = true)) {
                        isMatch = true
                    }
                    val players = iGame.getJSONArray("players")
                    for (j in 0 until players.length()) {
                        if (players.getString(j).contains(filter.text.toString(), ignoreCase = true)) {
                            isMatch = true
                        }
                    }
                    if (isMatch) {
                        filteredPublicGames.put(iGame.toString())
                    }
                }
            } else {
                filteredPublicGames = unfilteredPublicGames
            }

            val length = filteredPublicGames.length()
            val viewCount = pg.childCount

            if (viewCount >= 2) { // Remove everything apart from header and filter
                for (i in (viewCount - 1) downTo 2) {
                    pg.removeViewAt(i)
                }
            }

            if (length > 0) {
                for (i in 0 until length) {
                    val gameUuid = JSONObject(filteredPublicGames.get(i).toString()).getString("game_uuid")
                    val room = JSONObject(filteredPublicGames.get(i).toString()).getString("room")
                    val playersArray = JSONObject(filteredPublicGames.get(i).toString()).getJSONArray("players")
                    val playersList = ArrayList<String>()
                    for (j in 0 until playersArray.length()){
                        playersList.add(playersArray.getString(j))
                    }
                    val playersText = playersList.joinToString(separator = ", ")

                    val gameText = TextView(this@MainActivity)
                    gameText.setPadding(0, 30, 0, 0)
                    gameText.text = "${getString(R.string.room)} $room\n${getString(R.string.players)}: $playersText"
                    gameText.setOnClickListener {
                        val intent = Intent(this@MainActivity, Game::class.java)
                            .putExtra("game_uuid", gameUuid)
                        startActivity(intent)
                    }
                    pg.addView(gameText, i + 2)
                }
            } else {
                if (filter.text.toString() == "") {
                    val noGames = TextView(this@MainActivity)
                    noGames.text = getString(R.string.no_games)
                    pg.addView(noGames, 2)
                }
            }
        }

        message.observe(this@MainActivity, {
            generatePublicGames()
        })

        playerFilter.addTextChangedListener { generatePublicGames() }
    }

    override fun onResume() {
        super.onResume()
        val mHandler = Handler(Looper.getMainLooper())
        val client = OkHttpClient()
        val sharedPref = this.getSharedPreferences("app.blef.blef.MAIN", Context.MODE_PRIVATE)
        val lastGameUuid = sharedPref.getString("game_uuid", "")
        val continueButton = findViewById<Button>(R.id.continueGame)

        fun freezeContinueButton() {
            continueButton.setBackgroundColor(Color.GRAY)
            continueButton.isClickable = false
        }

        if (lastGameUuid == "") {
            freezeContinueButton()
        } else {
            val lastGameRequest = Request.Builder()
                .url("$baseUrl/$lastGameUuid")
                .build()

            client.newCall(lastGameRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            sharedPref.edit().putString("game_uuid", "")
                            mHandler.post { freezeContinueButton() }
                        } else {
                            val newMessage = response.body!!.string()
                            if (JSONObject(newMessage).getString("status") in setOf(GameStatuses.NOT_STARTED, GameStatuses.RUNNING)) {
                                val continueIntent = Intent(this@MainActivity, Game::class.java)
                                    .putExtra("game_uuid", sharedPref.getString("game_uuid", null))
                                mHandler.post {
                                    continueButton.isClickable = true
                                    continueButton.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.design_default_color_primary))
                                    continueButton.setOnClickListener { startActivity(continueIntent) }
                                }
                            } else {
                                sharedPref.edit().putString("game_uuid", "").apply()
                                mHandler.post { freezeContinueButton() }
                            }
                        }
                    }
                }
            })
        }
        Thread{ WebSettings.getDefaultUserAgent(this@MainActivity) }.start()
    }
}