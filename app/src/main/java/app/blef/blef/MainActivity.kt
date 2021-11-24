//  Created by Maciej Pomykala on 27.06.2021
//  Copyright © 2021 Blef Team.

package app.blef.blef

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.MutableLiveData
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.concurrent.fixedRateTimer

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mHandler = Handler(Looper.getMainLooper())

        val message = MutableLiveData<String>()

        val pixelDensity = resources.displayMetrics.density
        fun adjustForDensity(raw: Int): Int {
            return((raw * pixelDensity).toInt())
        }

        val redirectReason = intent.getStringExtra("reason").toString()
        if (redirectReason == "Invite failed") {
            AlertDialog.Builder(this)
                .setMessage("Invite link failed")
                .setPositiveButton("OK", null)
                .show()
        }

        val pg = findViewById<LinearLayout>(R.id.publicGames)
        val headerText = TextView(this@MainActivity)
        headerText.text = "Public games (tap to join):"
        val playerFilter = EditText(this@MainActivity)
        playerFilter.height = adjustForDensity(60)
        playerFilter.hint = "Filter room numbers / players..."
        playerFilter.tag = "player_filter"
        pg.addView(headerText)
        pg.addView(playerFilter)

        val sharedPref = this.getSharedPreferences("app.blef.blef.MAIN", Context.MODE_PRIVATE)
        val lastGameUuid = sharedPref.getString("game_uuid", "")
        val continueButton = findViewById<Button>(R.id.continueGame)

        val client = OkHttpClient()

        if (lastGameUuid != "") {
            val lastGameRequest = Request.Builder()
                .url("https://n4p6oovxsg.execute-api.eu-west-2.amazonaws.com/games/$lastGameUuid")
                .build()

            client.newCall(lastGameRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            sharedPref.edit().putString("game_uuid", "")
                            mHandler.post{ continueButton.visibility = View.GONE }
                        } else {
                            val newMessage = response.body!!.string()
                            if (JSONObject(newMessage).getString("status") == "Finished") {
                                sharedPref.edit().putString("game_uuid", "").apply()
                                mHandler.post{ continueButton.visibility = View.GONE }
                            } else {
                                val continueIntent = Intent(this@MainActivity, Game::class.java)
                                    .putExtra("game_uuid", sharedPref.getString("game_uuid", null))
                                mHandler.post{
                                    continueButton.visibility = View.VISIBLE
                                    continueButton.setOnClickListener {startActivity(continueIntent)}
                                }
                            }
                        }
                    }
                }
            })
        }

        val request = Request.Builder()
            .url("https://n4p6oovxsg.execute-api.eu-west-2.amazonaws.com/games")
            .build()

        fixedRateTimer("update_public_games", false, 0L, 1000) {
            if (hasWindowFocus()) {
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace()
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            if (!response.isSuccessful) {
                                showEngineError(R.id.activity_main, response)
                            } else {
                                val newMessage = response.body!!.string()
                                if (newMessage != message.value.toString()) {
                                    mHandler.post{
                                        message.setValue(newMessage)
                                    }
                                }
                            }
                        }
                    }
                })
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
                    gameText.text = "Room $room\n${getString(R.string.players)}: $playersText"
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

        //val intent = Intent(this@MainActivity, Game::class.java).apply {
        //    putExtra("game_uuid", "")
        //    putExtra("player_uuid", "")
        //    putExtra("nickname", "")
        //}

        //startActivity(intent)
    }
}