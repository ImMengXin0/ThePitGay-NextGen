package cn.charlotte.pit.data.sub

import cn.charlotte.pit.ThePit
import java.util.*

class KingsQuestsData {
    var currentKingQuestsUuid: UUID? = null

    var accepted = false
    var completed = false

    var killedPlayer = 0
    var collectedRenown = 0


    fun checkUpdate() {
        val runningUuid = ThePit.api?.runningKingsQuestsUuid ?: return
        if (currentKingQuestsUuid == null) {
            currentKingQuestsUuid = runningUuid
            return
        }
        if (currentKingQuestsUuid != runningUuid) {
            currentKingQuestsUuid = runningUuid
            accepted = false
            completed = false
            killedPlayer = 0
            collectedRenown = 0
        }
    }

}
