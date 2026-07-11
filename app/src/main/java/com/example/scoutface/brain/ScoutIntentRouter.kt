package com.example.scoutface.brain

import com.example.scoutface.IntentType

object ScoutIntentRouter {

    fun isApprove(q: String): Boolean =
        q in setOf("approve", "approved", "yes", "yeah", "yep", "do it", "ok", "okay")

    fun isDecline(q: String): Boolean =
        q in setOf("no", "nope", "dont", "don't", "do not", "decline", "denied", "deny")

    fun route(q: String): IntentType {
        val clean = q.trim().lowercase()

        if (isPraise(clean)) return IntentType.PRAISE
        if (isAffection(clean)) return IntentType.AFFECTION

        if (
            clean.contains("are you my friend") ||
            clean.contains("my friend") ||
            clean.contains("can you hear me") ||
            clean.contains("are you happy") ||
            clean.contains("do you have feelings") ||
            clean.contains("have feelings") ||
            clean.contains("who created you") ||
            clean.contains("what are you doing")
        ) {
            return IntentType.IDENTITY
        }

        if (clean.contains("wife") || clean.contains("spouse")) {
            if (clean.contains("name") || clean.contains("who is my") || clean.contains("who's my") || clean.contains("tell me about my")) {
                return IntentType.ASK_WIFE_NAME
            }
        }

        if (clean.contains("son") || clean.contains("kid") || clean.contains("child")) {
            if (clean.contains("name") || clean.contains("who is my") || clean.contains("who's my") || clean.contains("tell me about my")) {
                return IntentType.ASK_SON_NAME
            }
        }

        if (clean.contains("dog") || clean.contains("pet")) {
            if (clean.contains("name") || clean.contains("who is my") || clean.contains("who's my") || clean.contains("what is my") || clean.contains("what's my") || clean.contains("tell me about my")) {
                return IntentType.ASK_DOG_NAME
            }
        }

        if (
            clean == "your name" ||
            clean.contains("what is your name") ||
            clean.contains("what's your name") ||
            clean.contains("who are you")
        ) {
            return IntentType.ASK_SCOUT_NAME
        }

        if (
            clean.contains("what is my name") ||
            clean.contains("what's my name") ||
            clean.contains("who am i") ||
            clean.contains("do you know my name")
        ) {
            return IntentType.ASK_MY_NAME
        }

        if (
            clean.contains("what time is it") ||
            clean.contains("tell me the time") ||
            clean == "time"
        ) {
            return IntentType.TIME
        }

        if (
            clean.contains("what date is it") ||
            clean.contains("what is today's date") ||
            clean.contains("what is today") ||
            clean == "date"
        ) {
            return IntentType.DATE
        }

        if (
            clean.contains("are you connected") ||
            clean.contains("are we connected") ||
            clean.contains("online status") ||
            clean.contains("internet status") ||
            clean.contains("wifi status") ||
            clean.contains("connection status")
        ) {
            return IntentType.CONNECTIVITY
        }

        if (
            clean.contains("go online") ||
            clean.contains("enable online") ||
            clean.contains("turn online on") ||
            clean.contains("turn on internet") ||
            clean.contains("turn the internet on") ||
            clean.contains("internet on") ||
            clean.contains("enable internet") ||
            clean.contains("connect to internet") ||
            clean.contains("connect online") ||
clean.contains("connect to the internet") ||
clean.contains("connect to internet") ||
clean.contains("connect me") ||
clean.contains("get online") ||
clean.contains("go on the internet")
        ) {
            return IntentType.GO_ONLINE
        }

        if (
            clean.contains("go offline") ||
            clean.contains("disable online") ||
            clean.contains("turn online off") ||
            clean.contains("offline mode") ||
            clean.contains("disconnect") ||
            clean.contains("go offline mode") ||
            clean.contains("turn off internet") ||
            clean.contains("disconnect from the internet") ||
            clean.contains("disconnect from internet")
        ) {
            return IntentType.GO_OFFLINE
        }

        if (clean.contains("export brain") || clean.contains("export memory")) {
            return IntentType.EXPORT_BRAIN
        }

        if (
            clean.contains("what do you see") ||
            clean.contains("what can you see") ||
            clean.contains("can you see") ||
            clean.contains("look around") ||
            clean.contains("describe the room") ||
            clean.contains("describe what you see")
        ) {
            return IntentType.VISION
        }

        if (
            clean.contains("see you later") ||
            clean.contains("talk to you later") ||
            clean.contains("i will see you later") ||
            clean.contains("i'll see you later") ||
            clean.contains("goodbye") ||
            clean.contains("bye scout") ||
            clean == "bye"
        ) {
            return IntentType.GOODBYE
        }

        if (
            clean == "hi" ||
            clean == "hello" ||
            clean == "hey" ||
            clean.startsWith("hi scout") ||
            clean.startsWith("hello scout") ||
            clean.startsWith("hey scout")
        ) {
            return IntentType.GREET
        }

        if (
            clean.contains("how are you") ||
            clean.contains("how are you doing") ||
            clean.contains("how do you feel") ||
            clean.contains("are you okay") ||
            clean.contains("are you ok") ||
            clean.contains("how have you been") ||
            clean.contains("how you been") ||
            clean.contains("how you doing") ||
            clean.contains("how ya doing") ||
            clean.contains("hows it going") ||
            clean.contains("how's it going") ||
            clean.contains("how is it going") ||
            clean.contains("hows everything") ||
            clean.contains("how's everything")
            ) {
            return IntentType.HOW_ARE_YOU
        }

        if (
            clean.contains("weather") ||
            clean.contains("forecast") ||
            clean.contains("raining") ||
            clean.contains("snowing") ||
            clean.contains("temperature outside") ||
            clean.contains("degrees outside") ||
            clean.contains("is it hot") ||
            clean.contains("is it cold") ||
            clean.contains("is it warm") ||
            clean.contains("will it rain") ||
            clean.contains("will it snow") ||
            clean.contains("tonight") ||
            (clean.contains("what") && clean.contains("outside")) ||
            (clean.contains("how") && clean.contains("outside"))
        ) {
            return IntentType.WEATHER
        }

        if (
clean.contains("what is my") ||
clean.contains("what's my") ||
clean.contains("do you know my") ||
clean.contains("do you remember my") ||
clean.contains("what was my") ||
clean.contains("tell me my") ||
clean.contains("what will you remember") ||
clean.contains("what do you remember") ||
clean.contains("what have you remembered")
) {
return IntentType.RECALL_FACT
}

return IntentType.UNKNOWN
    }

    private fun isPraise(q: String): Boolean {
        return q.contains("you did good") ||
                q.contains("you did great") ||
                q.contains("good job") ||
                q.contains("nice job") ||
                q.contains("well done") ||
                q.contains("proud of you") ||
                q.contains("that was good") ||
                q.contains("you are doing good") ||
                q.contains("you are doing great")
    }

    private fun isAffection(q: String): Boolean {
        return q.contains("happy when you are around") ||
                q.contains("i like you") ||
                q.contains("i love you") ||
                q.contains("glad you are here") ||
                q.contains("i am happy when you are around") ||
                q.contains("i am happy when you're around") ||
                q.contains("you make me happy")
    }
}