//  Created by Maciej Pomykala on 27.06.2021
//  Copyright © 2021 Blef Team.

package app.blef.blef

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.stream.Collectors
import kotlin.concurrent.fixedRateTimer

val sets = arrayOf(
    "High card, 9",
    "High card, 10",
    "High card, J",
    "High card, Q",
    "High card, K",
    "High card, A",
    "Pair of 9s",
    "Pair of 10s",
    "Pair of Js",
    "Pair of Qs",
    "Pair of Ks",
    "Pair of As",
    "Two pair, 10s and 9s",
    "Two pair, Js and 9s",
    "Two pair, Js and 10s",
    "Two pair, Qs and 9s",
    "Two pair, Qs and 10s",
    "Two pair, Qs and Js",
    "Two pair, Ks and 9s",
    "Two pair, Ks and 10s",
    "Two pair, Ks and Js",
    "Two pair, Ks and Qs",
    "Two pair, As and 9s",
    "Two pair, As and 10s",
    "Two pair, As and Js",
    "Two pair, As and Qs",
    "Two pair, As and Ks",
    "Small straight (9-K)",
    "Big straight (10-A)",
    "Great straight (9-A)",
    "Three of a kind, 9s",
    "Three of a kind, 10s",
    "Three of a kind, Js",
    "Three of a kind, Qs",
    "Three of a kind, Ks",
    "Three of a kind, As",
    "Full house, 9s over 10s",
    "Full house, 9s over Js",
    "Full house, 9s over Qs",
    "Full house, 9s over Ks",
    "Full house, 9s over As",
    "Full house, 10s over 9s",
    "Full house, 10s over Js",
    "Full house, 10s over Qs",
    "Full house, 10s over Ks",
    "Full house, 10s over As",
    "Full house, Js over 9s",
    "Full house, Js over 10s",
    "Full house, Js over Qs",
    "Full house, Js over Ks",
    "Full house, Js over As",
    "Full house, Qs over 9s",
    "Full house, Qs over 10s",
    "Full house, Qs over Js",
    "Full house, Qs over Ks",
    "Full house, Qs over As",
    "Full house, Ks over 9s",
    "Full house, Ks over 10s",
    "Full house, Ks over Js",
    "Full house, Ks over Qs",
    "Full house, Ks over As",
    "Full house, As over 9s",
    "Full house, As over 10s",
    "Full house, As over Js",
    "Full house, As over Qs",
    "Full house, As over Ks",
    "Flush ♣",
    "Flush ♦",
    "Flush ♥",
    "Flush ♠",
    "Four of a kind, 9s",
    "Four of a kind, 10s",
    "Four of a kind, Js",
    "Four of a kind, Qs",
    "Four of a kind, Ks",
    "Four of a kind, As",
    "Small straight flush (9-K) ♣",
    "Small straight flush (9-K) ♦",
    "Small straight flush (9-K) ♥",
    "Small straight flush (9-K) ♠",
    "Big straight flush (10-A) ♣",
    "Big straight flush (10-A) ♦",
    "Big straight flush (10-A) ♥",
    "Big straight flush (10-A) ♠",
    "Great straight flush (9-A) ♣",
    "Great straight flush (9-A) ♦",
    "Great straight flush (9-A) ♥",
    "Great straight flush (9-A) ♠"
)

class Game : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        val pixelDensity = resources.displayMetrics.density

        val gameUuid = intent.getStringExtra("game_uuid").toString().lowercase()
        val playerUuid = intent.getStringExtra("player_uuid")
        val nickname = intent.getStringExtra("nickname")

        class ProperMutableLiveData<T> : MutableLiveData<T>() {
            override fun setValue(value: T?) {
                if (value != this.value) {
                    super.setValue(value)
                }
            }
        }
        val message = ProperMutableLiveData<String>()
        val updateOnHold = MutableLiveData<Boolean>()
        updateOnHold.value = false
        val gameFinished = MutableLiveData<Boolean>()
        gameFinished.value = false

        val mHandler = Handler(Looper.getMainLooper())
        val client = OkHttpClient()
        val baseUrl = "https://n4p6oovxsg.execute-api.eu-west-2.amazonaws.com/games"

        fun updateGame() {
            val queryUrl = baseUrl + if (playerUuid != null)  "/$gameUuid?player_uuid=$playerUuid" else "/$gameUuid"
            val request = Request.Builder()
                .url(queryUrl)
                .build()

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
                                val newJson = JSONObject(newMessage)
                                if (newJson.getString("round_number").toInt() <= 1 || // hard update if game not started or in first round
                                    (newJson.getString("status") == "Running" && // hard update if game has not progressed to another round and has not finished
                                    newJson.getString("round_number") == JSONObject(message.value.toString()).getString("round_number"))) {
                                    mHandler.post{message.setValue(newMessage)}
                                } else {
                                    if (newJson.getString("status") == "Finished") mHandler.post{gameFinished.value = true}
                                    // Update only the current round
                                    val currentRound = JSONObject(message.value.toString()).getString("round_number")
                                    val queryUrl2 = baseUrl + if (playerUuid != null)  "/$gameUuid?player_uuid=$playerUuid&round=$currentRound" else "/$gameUuid?round=$currentRound"
                                    val request2 = Request.Builder()
                                        .url(queryUrl2)
                                        .build()

                                    client.newCall(request2).enqueue(object : Callback {
                                        override fun onFailure(call: Call, e: IOException) {
                                            e.printStackTrace()
                                        }

                                        override fun onResponse(call: Call, response: Response) {
                                            response.use {
                                                if (!response.isSuccessful) {
                                                    val engineErrorBar = Snackbar.make(findViewById(
                                                        R.id.activity_game
                                                    ), response.body!!.string(), 3000)
                                                    engineErrorBar.show()
                                                } else {
                                                    val newMessage2 = response.body!!.string()
                                                    mHandler.post{
                                                        updateOnHold.value = true
                                                        message.setValue(newMessage2)
                                                    }
                                                }
                                            }
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            })
        }

        fun hardUpdateGame() {
            val queryUrl = baseUrl + if (playerUuid != null)  "/$gameUuid?player_uuid=$playerUuid" else "/$gameUuid"
            val request = Request.Builder()
                .url(queryUrl)
                .build()

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
                                    updateOnHold.value = false
                                    message.setValue(newMessage)
                                }
                            }
                        }
                    }
                }
            })
        }

        fun updateGameIfEngineHappy(response: Response) {
            if (!response.isSuccessful) {
                val engineErrorBar = Snackbar.make(findViewById(R.id.activity_game), response.body!!.string(), 3000)
                engineErrorBar.show()
            } else {
                updateGame()
            }
        }

        fun makePublic() {
            val queryUrl = "$baseUrl/$gameUuid/make-public?admin_uuid=$playerUuid"
            val request = Request.Builder()
                .url(queryUrl)
                .build()
            client.newCall(request).enqueue(object : Callback{
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }
                override fun onResponse(call: Call, response: Response) {
                    updateGameIfEngineHappy(response)
                }
            })
        }

        fun makePrivate() {
            val queryUrl = "$baseUrl/$gameUuid/make-private?admin_uuid=$playerUuid"
            val request = Request.Builder()
                .url(queryUrl)
                .build()
            client.newCall(request).enqueue(object : Callback{
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }
                override fun onResponse(call: Call, response: Response) {
                    updateGameIfEngineHappy(response)
                }
            })
        }

        fun start() {
            val queryUrl = "$baseUrl/$gameUuid/start?admin_uuid=$playerUuid"
            val request = Request.Builder()
                .url(queryUrl)
                .build()
            client.newCall(request).enqueue(object : Callback{
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }
                override fun onResponse(call: Call, response: Response) {
                    updateGameIfEngineHappy(response)
                }
            })
        }

        fun makeCell(s: String): String {
            return("<td>".plus(s).plus("</td>"))
        }
        fun makeRow(vararg ss: String): String {
            var out = "<tr>"
            for (s in ss) out += s
            return(out.plus("</tr>"))
        }
        fun makeTable(vararg ss: String): String {
            var out = "<table><tbody>"
            for (s in ss) out += s
            return(out.plus("</table></tbody>"))
        }
        fun addStyle(s: String): String {
            return("<style>".plus(assets.open("table_style.css").bufferedReader().lines().collect(Collectors.joining())).plus("</style>").plus(s))
        }

        val gi = findViewById<LinearLayout>(R.id.gameInfo)
        val generalInfo = WebView(this@Game)
        generalInfo.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        generalInfo.tag = "generalInfo"
        generalInfo.isVerticalScrollBarEnabled = false
        val playersIntro = TextView(this@Game)
        playersIntro.tag = "playersIntro"
        val playersInfo = WebView(this@Game)
        playersInfo.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        playersInfo.isVerticalScrollBarEnabled = false
        playersInfo.tag = "playersInfo"
        val historyIntro = TextView(this@Game)
        historyIntro.tag = "historyIntro"
        val historyInfo = WebView(this@Game)
        historyInfo.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        historyInfo.isVerticalScrollBarEnabled = false
        historyInfo.tag = "historyInfo"
        val handsIntro = TextView(this@Game)
        handsIntro.tag = "handsIntro"
        val handsInfo = WebView(this@Game)
        handsInfo.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        handsInfo.isVerticalScrollBarEnabled = false
        handsInfo.tag = "handsInfo"
        val statusInfo = TextView(this@Game)
        statusInfo.tag = "statusInfo"

        fun generatePreStartGameInfo(gameObject: JSONObject, gi: LinearLayout) {
            if (gi.findViewWithTag<WebView>("generalInfo") == null) gi.addView(generalInfo)
            if (gi.findViewWithTag<TextView>("playersIntro") == null) gi.addView(playersIntro)
            if (gi.findViewWithTag<WebView>("playersInfo") == null) gi.addView(playersInfo)
            if (gi.findViewWithTag<TextView>("statusInfo") == null) gi.addView(statusInfo)
            val generalInfoData = makeTable(
                makeRow(makeCell("UUID"), makeCell(gameUuid)),
                makeRow(makeCell("Visibility"), makeCell(if (gameObject.getString("public") == "true") "Public" else "Private")),
                makeRow(makeCell("Admin"), makeCell(gameObject.getString("admin_nickname")))
            )
            generalInfo.loadData(addStyle(generalInfoData), "text/html", "UTF-8")

            playersIntro.text = "Players:"
            var playersInfoData = ""
            val playersArray = gameObject.getJSONArray("players")
            for (i in 0 until playersArray.length()) {
                playersInfoData = playersInfoData.plus(makeRow(makeCell(playersArray.getJSONObject(i).getString("nickname"))))
            }
            playersInfoData = addStyle(makeTable(playersInfoData))
            playersInfo.loadData(playersInfoData, "text/html", "UTF-8")

            statusInfo.text = "The game has not started yet."
        }

        fun generateRunningGameInfo(gameObject: JSONObject, gi: LinearLayout) {
            if (gi.findViewWithTag<WebView>("generalInfo") == null) gi.addView(generalInfo)
            if (gi.findViewWithTag<TextView>("playersIntro") == null) gi.addView(playersIntro)
            if (gi.findViewWithTag<WebView>("playersInfo") == null) gi.addView(playersInfo)
            if (gi.findViewWithTag<TextView>("historyIntro") == null) gi.addView(historyIntro)
            if (gi.findViewWithTag<WebView>("historyInfo") == null) gi.addView(historyInfo)
            if (gi.findViewWithTag<TextView>("handsIntro") == null) gi.addView(handsIntro)
            if (gi.findViewWithTag<WebView>("handsInfo") == null) gi.addView(handsInfo)
            if (gi.findViewWithTag<TextView>("statusInfo") != null && gi.getChildAt(gi.childCount - 1) != statusInfo) {
                gi.removeView(statusInfo) // Status info should not be in the middle
            }
            if (gi.findViewWithTag<TextView>("statusInfo") == null) gi.addView(statusInfo)

            val generalInfoData = makeTable(
                makeRow(makeCell("Visibility"), makeCell(if (gameObject.getString("public") == "true") "Public" else "Private")),
                makeRow(makeCell("Max cards"), makeCell(gameObject.getString("max_cards")))
            )
            generalInfo.loadData(addStyle(generalInfoData), "text/html", "UTF-8")

            playersIntro.text = "Players:"
            var playersInfoData = ""
            val playersArray = gameObject.getJSONArray("players")
            for (i in 0 until playersArray.length()) {
                val iNickname = playersArray.getJSONObject(i).getString("nickname")
                val iCards = playersArray.getJSONObject(i).getInt("n_cards")
                playersInfoData = playersInfoData.plus(makeRow(
                    makeCell(if (iNickname == nickname) "$iNickname (You)" else iNickname),
                    makeCell(if (iCards == 0) "Lost" else iCards.toString())
                ))
            }
            playersInfo.loadData(addStyle(makeTable(playersInfoData)), "text/html", "UTF-8")

            historyIntro.text = "Actions so far:"
            var historyInfoData = ""
            val historyArray = gameObject.getJSONArray("history")
            var roundEnded = false
            var loser = ""
            if (historyArray.length() > 0) {
                for (i in 0 until historyArray.length()) {
                    val iNickname = historyArray.getJSONObject(i).getString("player")
                    val iAction = historyArray.getJSONObject(i).getInt("action_id")
                    if (iAction <= 88) {
                        historyInfoData = historyInfoData.plus(makeRow(
                            makeCell(if (iNickname == nickname) "$iNickname (You)" else iNickname),
                            makeCell(if (iAction <= 87) sets[iAction] else "Check")
                        ))
                    } else {
                        roundEnded = true
                        loser = iNickname
                    }
                }
            } else {
                historyInfoData = makeRow(makeCell("No bets so far"))
            }
            historyInfo.loadData(addStyle(makeTable(historyInfoData)), "text/html", "UTF-8")

            val handsArray = gameObject.getJSONArray("hands")
            var handsInfoData = ""
            if (!roundEnded && nickname != null) {
                handsIntro.text = "Your hand:"
                for (i in 0 until handsArray.length()) {
                    val cardValue = handsArray.getJSONObject(i).getString("value")
                    val cardColour = handsArray.getJSONObject(i).getString("colour")
                    handsInfoData = handsInfoData
                        .plus("<img src=\"cards/specific/$cardValue$cardColour.png\" width=\"55\" height=\"55\">")
                }
                println(handsInfoData)
                handsInfo.loadDataWithBaseURL("file:///android_asset/", handsInfoData, "text/html", "UTF-8", null)
            } else if (roundEnded) {
                handsIntro.text = "Hands:"
                for (i in 0 until playersArray.length()) {
                    val iNickname = playersArray.getJSONObject(i).getString("nickname")
                    var cardsData = ""
                    for (j in 0 until handsArray.length()) {
                        val cardValue = handsArray.getJSONObject(j).getString("value")
                        val cardColour = handsArray.getJSONObject(j).getString("colour")
                        if (handsArray.getJSONObject(j).getString("player") == iNickname) {
                            cardsData = cardsData.plus("<img src=\"cards/specific/$cardValue$cardColour.png\" width=\"55\" height=\"55\">")
                        }
                    }
                    handsInfoData = handsInfoData.plus(makeRow(
                        makeCell(if (iNickname == nickname) "$iNickname (You)" else iNickname),
                        makeCell(cardsData)
                    ))
                }
                handsInfo.loadDataWithBaseURL("file:///android_asset/", addStyle(makeTable(handsInfoData)), "text/html", "UTF-8", null)
            } else {
                handsIntro.text = null
                handsInfo.loadData("", "text/html", "UTF-8")
            }

            when {
                roundEnded && loser == nickname -> {
                    statusInfo.text = "You lost the round."
                }
                roundEnded && loser != nickname -> {
                    statusInfo.text = "$loser lost the round."
                }
                !roundEnded && gameObject.getString("cp_nickname") == nickname -> {
                    statusInfo.text = "Make a move:"
                }
                !roundEnded && gameObject.getString("cp_nickname") != nickname -> {
                    statusInfo.text = "Current player: ${gameObject.getString("cp_nickname")}"
                }
            }
        }

        fun generateFinishedGameInfo(gameObject: JSONObject, gi: LinearLayout) {
            if (gi.findViewWithTag<WebView>("generalInfo") == null) gi.addView(generalInfo)
            if (gi.findViewWithTag<TextView>("playersIntro") == null) gi.addView(playersIntro)
            if (gi.findViewWithTag<WebView>("playersInfo") == null) gi.addView(playersInfo)
            if (gi.findViewWithTag<TextView>("statusInfo") == null) gi.addView(statusInfo)

            if (gi.findViewWithTag<TextView>("historyIntro") != null) gi.removeView(historyIntro)
            if (gi.findViewWithTag<WebView>("historyInfo") != null) gi.removeView(historyInfo)
            if (gi.findViewWithTag<TextView>("handsIntro") != null) gi.removeView(handsIntro)
            if (gi.findViewWithTag<TextView>("handsInfo") != null) gi.removeView(handsInfo)

            val generalInfoData = makeTable(
                makeRow(makeCell("Visibility"), makeCell(if (gameObject.getString("public") == "true") "Public" else "Private")),
                makeRow(makeCell("Max cards"), makeCell(gameObject.getString("max_cards")))
            )
            generalInfo.loadData(addStyle(generalInfoData), "text/html", "UTF-8")

            playersIntro.text = "Results:"
            var playersInfoData = ""
            val playersArray = gameObject.getJSONArray("players")
            for (i in 0 until playersArray.length()) {
                val iNickname = playersArray.getJSONObject(i).getString("nickname")
                val iCards = playersArray.getJSONObject(i).getInt("n_cards")
                playersInfoData = playersInfoData.plus(makeRow(
                    makeCell(if (iNickname == nickname) "$iNickname (You)" else iNickname),
                    makeCell(if (iCards == 0) "" else "\uD83D\uDC51")
                ))
            }
            playersInfo.loadData(addStyle(makeTable(playersInfoData)), "text/html", "UTF-8")

            statusInfo.text = "The game has finished."
        }

        fun generateGameInfo(gameObject: JSONObject) {
            when {
                gameObject.getString("status") == "Not started" -> {
                    generatePreStartGameInfo(gameObject, gi)
                }
                gameObject.getString("status") == "Running" -> {
                    generateRunningGameInfo(gameObject, gi)
                }
                gameObject.getString("status") == "Finished" -> {
                    generateFinishedGameInfo(gameObject, gi)
                }
            }
        }

        fun sendAction(actionId: Int) {
            val queryUrl = "$baseUrl/$gameUuid/play?player_uuid=$playerUuid&action_id=$actionId"
            val request = Request.Builder()
                .url(queryUrl)
                .build()
            client.newCall(request).enqueue(object : Callback{
                override fun onFailure(call: Call, e: IOException) {
                    e.printStackTrace()
                }
                override fun onResponse(call: Call, response: Response) {
                    updateGameIfEngineHappy(response)
                }
            })
        }

        val confirmButton = MaterialButton(this@Game)
        confirmButton.text = "Confirm bet"
        confirmButton.height = (60 * pixelDensity).toInt()
        confirmButton.setOnClickListener {
            sendAction(sets.indexOf(findViewById<Spinner>(0).selectedItem.toString()))
        }

        val checkButton = MaterialButton(this@Game)
        checkButton.text = "Check"
        checkButton.height = (60 * pixelDensity).toInt()
        checkButton.setOnClickListener {
            sendAction(88)
        }

        val updateButton = MaterialButton(this@Game)
        updateButton.tag = "update"
        updateButton.text = "Go to current round"
        updateButton.height = (60 * pixelDensity).toInt()
        updateButton.setOnClickListener{hardUpdateGame()}

        val startButton = MaterialButton(this@Game)
        startButton.tag = "start"
        startButton.text = "Start game"
        startButton.height = (60 * pixelDensity).toInt()
        startButton.setOnClickListener{start()}

        val inviteButton = MaterialButton(this@Game)
        inviteButton.tag = "inviteButton"
        inviteButton.text = "Send invite"
        inviteButton.height = (60 * pixelDensity).toInt()
        inviteButton.setOnClickListener{
            val link = "Join me for a game of Blef: https://blef.app/?link=http://blef.app/$gameUuid"
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, link)
            startActivity(Intent.createChooser(intent, "Share via"))
        }

        val publicPrivateButton = MaterialButton(this@Game)
        publicPrivateButton.height = (60 * pixelDensity).toInt()

        fun makeBetChooser(lastActionId: Int): Spinner {
            val betChooser = Spinner(this@Game)
            betChooser.prompt = "Choose a bet..."
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sets.copyOfRange(lastActionId + 1, 87))
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            betChooser.adapter = adapter
            betChooser.id = 0
            betChooser.setPadding(0, 0, 0, 20)
            return(betChooser)
        }

        fun generateGameControls(gameObject: JSONObject) {
            val ll = findViewById<LinearLayout>(R.id.controlPanel)
            ll.removeAllViews()

            val history = gameObject.getJSONArray("history")

            if (updateOnHold.value == true) {
                ll.removeAllViews()
                ll.addView(updateButton)
            } else if (gameObject.getString("admin_nickname") == nickname && gameObject.getString("status") == "Not started") {
                ll.removeAllViews()
                ll.addView(startButton)
                if (gameObject.getString("public") == "false") {
                    publicPrivateButton.tag = "make_public"
                    publicPrivateButton.text = "Make public"
                    publicPrivateButton.setOnClickListener{makePublic()}
                } else {
                    publicPrivateButton.tag = "make_private"
                    publicPrivateButton.text = "Make private"
                    publicPrivateButton.setOnClickListener{makePrivate()}
                }
                ll.addView(publicPrivateButton)
            } else if (gameObject.getString("cp_nickname") == nickname) {
                // Time to move
                if (gameObject.getJSONArray("history").length() == 0) {
                    ll.addView(makeBetChooser(-1))
                    ll.addView(confirmButton)
                } else if (history.getJSONObject(history.length() - 1).getInt("action_id") in 0..86) {
                    val lastAction = history.getJSONObject(history.length() - 1).getInt("action_id")
                    ll.addView(makeBetChooser(lastAction))
                    ll.addView(confirmButton)
                    ll.addView(checkButton)
                } else {
                    ll.addView(checkButton)
                }
            }

            if (gameObject.getString("status") == "Not started") {
                ll.addView(inviteButton)
            }
        }

        message.observe(this@Game, {
            val gameObject = JSONObject(message.value.toString())
            generateGameInfo(gameObject)
            generateGameControls(gameObject)
        })

        hardUpdateGame()

        fixedRateTimer("update_game", false, 0L, 1000) {
            if (hasWindowFocus() && gameFinished.value == false && updateOnHold.value == false) updateGame()
        }
    }
}

