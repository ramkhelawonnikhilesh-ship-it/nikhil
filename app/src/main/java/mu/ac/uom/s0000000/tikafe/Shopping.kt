package mu.ac.uom.s0000000.tikafe

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

@Composable
fun locale(): Locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ENGLISH
@Composable
fun money(amount: Int, cents: Boolean = false): String =
    NumberFormat.getCurrencyInstance(locale()).apply {
        currency = Currency.getInstance("MUR")
        minimumFractionDigits = if (cents) 2 else 0
        maximumFractionDigits = minimumFractionDigits
    }.format(if (cents) amount / 100.0 else amount.toDouble())

@Composable
fun stamps(count: Int) = pluralStringResource(R.plurals.stamps,count,count)
@Composable
fun optionLabel(options: List<Choice>) = options.map { stringResource(it.label) }.joinToString(" · ")

@Composable
fun Menu(state: ShopState, branch: Branch, stock: Map<String,Int>) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!state.bakery,{ state.bakery = false },{ Text(stringResource(R.string.drinks)) })
            FilterChip(state.bakery,{ state.bakery = true },{ Text(stringResource(R.string.bakery)) })
        }
        BranchPicker(branch,R.string.pickup_branch,modifier = Modifier.padding(horizontal = 16.dp)) { state.choose(it.id) }
        LazyColumn(contentPadding = PaddingValues(16.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(products.filter { it.drink != state.bakery },key = { it.id }) { product ->
                val remaining = stock[product.id] ?: Int.MAX_VALUE
                val used = state.lines.filter { it.product == product }.sumOf { it.qty }
                Card(onClick = {
                    if (product.drink) state.customise(product) else state.add(Line(product))
                },enabled = remaining > used, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                    ProductImage(product)
                    Column(Modifier.padding(14.dp),verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(product.name),style = MaterialTheme.typography.titleLarge)
                        Text(money(product.price))
                        if (!product.drink) Text(when (remaining) {
                            0 -> stringResource(R.string.sold_out)
                            in 1..4 -> stringResource(R.string.remaining,remaining)
                            else -> stringResource(R.string.available)
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun ProductImage(product: Product) {
    Image(painterResource(product.photo),null,Modifier.fillMaxWidth().height(150.dp),contentScale = ContentScale.Crop)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Customise(state: ShopState, product: Product) = Page {
    Text(stringResource(product.name),style = MaterialTheme.typography.headlineSmall)
    ProductImage(product)
    if (product.sizes) Options(R.string.size,sizes,state)
    if (product.milk) Options(R.string.milk,milks,state)
    if (product.shot) Toggle(R.string.shot,extraShot in state.draft) { state.option("shot",extraShot.takeIf { _ -> it }) }
    Options(R.string.sugar,sugars,state)
    val line = Line(product,state.draft)
    Button(onClick = { state.add(line); state.screen = Screen.MENU },modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.add,money(line.unit)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Options(title: Int, choices: List<Choice>, state: ShopState) {
    Caption(title,"")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            FilterChip(choice in state.draft,{ state.option(choice.group,choice) },
                { Text(stringResource(choice.label) + if (choice.price > 0) " + " + money(choice.price) else "") },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
fun BranchPicker(selected: Branch, label: Int, modifier: Modifier = Modifier, choose: (Branch) -> Unit) =
    Picker(label,selected.name,branches,{ it.name },modifier,choose)

@Composable
private fun <T> Picker(label: Int, current: String, options: List<T>, names: @Composable (T) -> String,
                       modifier: Modifier = Modifier, select: (T) -> Unit) {
    var more by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { more = true },modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(label) + " · " + current)
        }
        DropdownMenu(expanded = more,onDismissRequest = { more = false },modifier = Modifier.heightIn(max = 350.dp)) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(names(option)) }, onClick = { select(option); more = false })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Basket(state: ShopState, user: User, branch: Branch, checkout: () -> Unit) = Page {
    val lines = state.lines
    if (lines.isEmpty()) {
        Text(stringResource(R.string.empty))
        return@Page
    }
    lines.forEach { line ->
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(line.product.name),style = MaterialTheme.typography.titleMedium)
                if (line.options.isNotEmpty()) Text(optionLabel(line.options))
                Text(money(line.total))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val remove = stringResource(R.string.decrease)
                    val add = stringResource(R.string.increase)
                    TextButton(onClick = { state.quantity(line,line.qty-1) },
                        modifier = Modifier.sizeIn(48.dp,48.dp).semantics { contentDescription = remove }) { Text("−") }
                    Text(NumberFormat.getIntegerInstance(locale()).format(line.qty),Modifier.padding(top = 13.dp))
                    TextButton(onClick = { state.quantity(line,line.qty+1) },
                        modifier = Modifier.sizeIn(48.dp,48.dp).semantics { contentDescription = add }) { Text("+") }
                    TextButton(onClick = { state.quantity(line,0) },modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.remove))
                    }
                }
            }
        }
    }
    val eligible = user.stamps >= 10 && lines.any { it.product.drink }
    if (eligible) Toggle(R.string.redeem,state.redeem) { state.redeem = it }
    BillRows(bill(lines,eligible && state.redeem))
    BranchPicker(branch,R.string.pickup_branch) { state.choose(it.id) }
    var now by remember { mutableStateOf(muNow()) }
    LaunchedEffect(Unit) { while (true) { now = muNow(); delay(15_000) } }
    val slots = remember(branch,now) { branch.slots(now) }
    val current = state.pickup?.takeIf { it in slots }
    val locale = locale()
    Picker(R.string.pickup_time,current?.let { timeLabel(it,locale) } ?: stringResource(R.string.choose),slots,
        { timeLabel(it,locale) }) { state.pickup = it }
    Button(onClick = checkout, enabled = current != null,modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.place_order))
    }
}

@Composable
fun BillRows(price: Bill) {
    HorizontalDivider()
    BillRow(stringResource(R.string.subtotal),money(price.subtotal))
    if (price.discount > 0) BillRow(stringResource(R.string.discount),"−" + money(price.discount))
    BillRow(stringResource(R.string.vat),money(price.vatCents,true))
    BillRow(stringResource(R.string.total),money(price.total),true)
}

@Composable
private fun BillRow(label: String, value: String, live: Boolean = false) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        if (live) liveRegion = LiveRegionMode.Polite
    },horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label,Modifier.weight(1f))
        Text(value)
    }
}

@Composable
fun Orders(state: ShopState, orders: List<Order>, summary: Pair<Int,Product?>) = Page {
    Text(stringResource(R.string.monthly,money(summary.first)))
    summary.second?.let { Text(stringResource(R.string.popular,stringResource(it.name))) }
    if (orders.isEmpty()) Text(stringResource(R.string.no_orders))
    orders.forEach { order -> OrderCard(order) { state.details(order.id) } }
}

@Composable
private fun OrderCard(order: Order, open: () -> Unit) {
    OutlinedCard(onClick = open,modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(stringResource(R.string.order_number,order.id),style = MaterialTheme.typography.titleMedium)
            Text(branch(order.branch).name)
            Text(timeLabel(order.pickup,locale()))
            Text(stringResource(order.status.label) + " · " + money(order.bill.total))
            if (order.arriving) Text(stringResource(R.string.arriving))
        }
    }
}

@Composable
fun Details(order: Order, lines: List<Line>, customer: Boolean, cancel: () -> Unit, again: () -> Unit,
            permission: @Composable () -> Unit) = Page {
    Text(stringResource(R.string.order_number,order.id),style = MaterialTheme.typography.headlineSmall)
    Text(branch(order.branch).name)
    Text(timeLabel(order.pickup,locale()))
    Text(stringResource(order.status.label))
    Text(stringResource(R.string.pickup_code,order.code),style = MaterialTheme.typography.headlineSmall)
    if (order.arriving) Text(stringResource(R.string.arriving))
    HorizontalDivider()
    lines.forEach {
        Text(stringResource(it.product.name) + " × " + it.qty + " · " + money(it.total))
        if (it.options.isNotEmpty()) Text(optionLabel(it.options))
    }
    BillRows(order.bill)
    if (customer) {
        if (order.status == Status.RECEIVED) OutlinedButton(onClick = cancel) { Text(stringResource(R.string.cancel)) }
        OutlinedButton(onClick = again) { Text(stringResource(R.string.again)) }
        if (order.status in listOf(Status.RECEIVED,Status.PREPARING,Status.READY)) permission()
    }
}

@Composable
fun Staff(state: ShopState, orders: List<Order>, quantities: Map<String,Int>, stock: (Product,Int) -> Unit,
          offer: () -> Unit, advance: (Order) -> Unit) = Page {
    Caption(R.string.daily_stock,"")
    products.filterNot { it.drink }.forEach { product ->
        val count = quantities.getValue(product.id)
        var input by remember(product,count) { mutableStateOf(count.toString()) }
        OutlinedTextField(input,{ input = it.filter(Char::isDigit).take(5) },label = { Text(stringResource(product.name)) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),singleLine = true)
        val amount = input.toIntOrNull()
        TextButton(onClick = { amount?.let { stock(product,it) } },enabled = amount != null && amount != count) {
            Text(stringResource(R.string.save))
        }
    }
    OutlinedButton(onClick = offer) { Text(stringResource(R.string.offer_button)) }
    Caption(R.string.orders,"")
    if (orders.isEmpty()) Text(stringResource(R.string.no_orders))
    orders.forEach { order ->
        OrderCard(order) { state.details(order.id,Screen.STAFF) }
        val next = when (order.status) {
            Status.RECEIVED -> Status.PREPARING
            Status.PREPARING -> Status.READY
            Status.READY -> Status.COLLECTED
            else -> null
        }
        if (next != null) Button(onClick = { advance(order) }) { Text(stringResource(next.label)) }
    }
}
