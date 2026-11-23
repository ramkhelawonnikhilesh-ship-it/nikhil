package mu.ac.uom.s0000000.tikafe

import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class Product(val id: String, val name: Int, val price: Int, val photo: Int, val bake: Int = 0,
    val sizes: Boolean = false, val milk: Boolean = false, val shot: Boolean = false) {
    val drink get() = bake == 0
}
val products = listOf(
    Product("D01", R.string.d01, 60, R.drawable.photo_d01, shot = true),
    Product("D02", R.string.d02, 95, R.drawable.photo_d02, sizes = true, milk = true, shot = true),
    Product("D03", R.string.d03, 100, R.drawable.photo_d03, sizes = true, milk = true, shot = true),
    Product("D04", R.string.d04, 80, R.drawable.photo_d04, sizes = true, milk = true, shot = true),
    Product("D05", R.string.d05, 70, R.drawable.photo_d05, sizes = true, milk = true),
    Product("D06", R.string.d06, 90, R.drawable.photo_d06, sizes = true),
    Product("D07", R.string.d07, 120, R.drawable.photo_d07, sizes = true, milk = true, shot = true),
    Product("D08", R.string.d08, 85, R.drawable.photo_d08, sizes = true),
    Product("B01", R.string.b01, 45, R.drawable.photo_b01, 12),
    Product("B02", R.string.b02, 55, R.drawable.photo_b02, 10),
    Product("B03", R.string.b03, 35, R.drawable.photo_b03, 15),
    Product("B04", R.string.b04, 30, R.drawable.photo_b04, 20),
    Product("B05", R.string.b05, 60, R.drawable.photo_b05, 8),
    Product("B06", R.string.b06, 25, R.drawable.photo_b06, 30),
    Product("B07", R.string.b07, 120, R.drawable.photo_b07, 6)
)
val productsById = products.associateBy { it.id }
data class Choice(val group: String, val key: String, val label: Int, val price: Int = 0)
val sizes = listOf(Choice("size","small",R.string.small), Choice("size","medium",R.string.medium,20), Choice("size","large",R.string.large,35))
val milks = listOf(Choice("milk","full",R.string.full), Choice("milk","skim",R.string.skim), Choice("milk","soy",R.string.soy,20), Choice("milk","oat",R.string.oat,25))
val sugars = listOf(Choice("sugar","none",R.string.none), Choice("sugar","less",R.string.less), Choice("sugar","normal",R.string.normal), Choice("sugar","extra",R.string.extra))
val extraShot = Choice("shot","shot",R.string.shot,30)
val choices = (sizes + milks + listOf(extraShot) + sugars).associateBy { it.key }

data class Line(val product: Product, val options: List<Choice> = emptyList(), val qty: Int = 1) {
    val unit get() = product.price + options.sumOf { it.price }
    val total get() = unit * qty
}

data class Bill(val subtotal: Int, val discount: Int) {
    val vatCents get() = (subtotal - discount) * 15
    val total get() = ((subtotal - discount) * 100 + vatCents + 50) / 100
}
fun bill(lines: List<Line>, redeem: Boolean) = Bill(lines.sumOf { it.total },
    if (redeem) lines.filter { it.product.drink }.maxOfOrNull { it.unit } ?: 0 else 0)

val muZone: ZoneId = ZoneId.of("Indian/Mauritius")
fun muNow(): ZonedDateTime = ZonedDateTime.now(muZone)
data class Branch(val id: String, val name: String, val lat: Double, val lon: Double) {
    fun hours(date: LocalDate): Pair<LocalTime, LocalTime>? {
        val day = date.dayOfWeek
        val times = when (id) {
            "PL" -> if (day == DayOfWeek.SUNDAY) "08:00" to "14:00" else "07:00" to "19:00"
            "RH" -> if (day == DayOfWeek.SUNDAY) null else "06:30" to "18:30"
            "CP" -> "07:00" to "18:00"
            "GB" -> "07:00" to "21:00"
            else -> if (day.value >= 6) null else "07:30" to "17:00"
        }
        return times?.let { LocalTime.parse(it.first) to LocalTime.parse(it.second) }
    }
    fun open(now: ZonedDateTime): Boolean = hours(now.toLocalDate())?.let {
        now.toLocalTime() >= it.first && now.toLocalTime() < it.second
    } ?: false
    fun validSlot(slot: Long, now: ZonedDateTime): Boolean {
        val selected = Instant.ofEpochMilli(slot).atZone(muZone)
        val time = selected.toLocalTime()
        val hours = hours(selected.toLocalDate()) ?: return false
        return selected >= now.plusMinutes(20) && time.minute % 15 == 0 &&
            time.second == 0 && time.nano == 0 && time >= hours.first && time < hours.second
    }
    fun slots(now: ZonedDateTime): List<Long> = buildList {
        repeat(7) { offset ->
            val date = now.toLocalDate().plusDays(offset.toLong())
            val (first, last) = hours(date) ?: return@repeat
            var time = first
            while (time < last) {
                val slot = date.atTime(time).atZone(muZone).toInstant().toEpochMilli()
                if (validSlot(slot, now)) add(slot)
                time = time.plusMinutes(15)
            }
        }
    }
}
val branches = listOf(
    Branch("PL","Port Louis (Caudan)",-20.1610,57.4980),
    Branch("RH","Rose Hill",-20.2400,57.4700),
    Branch("CP","Curepipe",-20.3170,57.5260),
    Branch("GB","Grand Baie",-20.0130,57.5810),
    Branch("UOM","University of Mauritius, Réduit",-20.2340,57.4980)
)
fun branch(id: String) = branches.first { it.id == id }
fun timeLabel(epoch: Long, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale).format(Instant.ofEpochMilli(epoch).atZone(muZone))
enum class Status(val label: Int) {
    RECEIVED(R.string.received), PREPARING(R.string.preparing), READY(R.string.ready),
    COLLECTED(R.string.collected), CANCELLED(R.string.cancelled)
}
