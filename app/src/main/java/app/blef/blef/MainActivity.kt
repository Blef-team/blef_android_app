//  Created by Maciej Pomykala on 27.06.2021
//  Copyright © 2021 Blef Team.

package app.blef.blef

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.MutableLiveData
import com.google.android.material.snackbar.Snackbar
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

        val pg = findViewById<LinearLayout>(R.id.publicGames)
        val headerText = TextView(this@MainActivity)
        headerText.text = "Public games (tap to join):"
        val playerFilter = EditText(this@MainActivity)
        playerFilter.hint = "Filter UUIDs / players..."
        playerFilter.tag = "player_filter"
        pg.addView(headerText)
        pg.addView(playerFilter)

        val client = OkHttpClient()
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
                                val engineErrorBar = Snackbar.make(findViewById(R.id.activity_game), response.body!!.string(), 3000)
                                engineErrorBar.show()
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

        findViewById<Button>(R.id.join).setOnClickListener {
            val intent = Intent(this, JoiningWithoutUuid::class.java)
            startActivity(intent)
        }

        fun generatePublicGames() {
            val unfilteredPublicGames = JSONArray(message.value.toString())
            var filteredPublicGames = JSONArray()
            val filter = findViewById<LinearLayout>(R.id.publicGames).findViewWithTag<EditText>("player_filter")
            if (filter != null) {
                for (i in 0 until unfilteredPublicGames.length()) {
                    var isMatch = false
                    val iGame = unfilteredPublicGames.getJSONObject(i)
                    if (iGame.getString("uuid").contains(filter.text.toString(), ignoreCase = true)) {
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
                    val gameUuid = JSONObject(filteredPublicGames.get(i).toString()).getString("uuid")
                    val playersArray = JSONObject(filteredPublicGames.get(i).toString()).getJSONArray("players")
                    val playersList = ArrayList<String>()
                    for (j in 0 until playersArray.length()){
                        playersList.add(playersArray.getString(j))
                    }
                    val playersText = playersList.joinToString(separator = ", ")

                    val startedText = if (JSONObject(filteredPublicGames.get(i).toString()).getString("started") == "false") {
                        "Not yet started"
                    } else {
                        "Already started"
                    }

                    val gameText = TextView(this@MainActivity)
                    gameText.setPadding(0, 30, 0, 0)
                    gameText.text = "$gameUuid\n$startedText\n${getString(R.string.players)}: $playersText"
                    gameText.setOnClickListener {
                        val uuid = JSONObject(filteredPublicGames.get(i).toString()).getString("uuid")
                        val intent = Intent(this@MainActivity, JoiningWithUuid::class.java).apply {
                            putExtra("game_uuid", uuid)
                        }
                        startActivity(intent)
                    }
                    pg.addView(gameText, i + 2)
                }
            } else {
                if (filter == null) {
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
}