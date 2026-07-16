package com.dlunaunizar.bobitos.feature.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlunaunizar.bobitos.core.common.UiState
import com.dlunaunizar.bobitos.core.model.CalendarEvent
import com.dlunaunizar.bobitos.core.model.EventColor
import com.dlunaunizar.bobitos.core.model.SpaceMember
import com.dlunaunizar.bobitos.data.repository.EventInput
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(spaceId: String, canWrite: Boolean, modifier: Modifier = Modifier, viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle(); var editor by remember { mutableStateOf<CalendarEvent?>(null) }; var creating by remember { mutableStateOf(false) }
    DisposableEffect(spaceId) { viewModel.observe(spaceId); onDispose(viewModel::stop) }
    Column(modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick=viewModel::previous) { Text("‹") }; Text(state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style=MaterialTheme.typography.titleLarge)
            TextButton(onClick=viewModel::next) { Text("›") }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick=viewModel::toggleMode) { Text(if(state.agenda) "Mes" else "Agenda") }; Button(enabled=canWrite,onClick={creating=true}) { Text("Nuevo evento") } }
        val events=(state.events as? UiState.Content)?.value.orEmpty()
        if (!state.agenda) MonthGrid(state.month,state.selectedDate,events,viewModel::select)
        Text(if(state.agenda) "Próximos eventos" else "Agenda del ${state.selectedDate}", style=MaterialTheme.typography.titleMedium)
        val shown=events.filter { if(state.agenda) !it.endAt.isBefore(Instant.now()) else it.displayStartDate(ZoneId.systemDefault())==state.selectedDate }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)) { items(shown,key={it.id}) { event -> EventRow(event,canWrite,{editor=event},{viewModel.delete(event.id)}) } }
        state.message?.let { Text(it); LaunchedEffect(it) { viewModel.clearMessage() } }
    }
    if(creating || editor!=null) EventEditor(editor,state.selectedDate,(state.members as? UiState.Content)?.value.orEmpty(),state.saving,{creating=false;editor=null}) { id,input -> viewModel.save(id,input); creating=false;editor=null }
}

@Composable private fun MonthGrid(month: YearMonth, selected: LocalDate, events: List<CalendarEvent>, select:(LocalDate)->Unit) {
    val zone=ZoneId.systemDefault(); val interval=month.visibleInterval(zone); val first=interval.start.atZone(zone).toLocalDate()
    Column { listOf("L","M","X","J","V","S","D").chunked(7).forEach { Row { it.forEach { Text(it,Modifier.weight(1f)) } } }; (0L until 42L).map(first::plusDays).chunked(7).forEach { week -> Row { week.forEach { date ->
        val count=events.count { it.displayStartDate(zone)==date }; Text("${date.dayOfMonth}${if(count>0) " •$count" else ""}",Modifier.weight(1f).clickable{select(date)}.padding(7.dp),color=if(date==selected) MaterialTheme.colorScheme.primary else LocalContentColor.current)
    } } } }
}
@Composable private fun EventRow(event:CalendarEvent,canWrite:Boolean,edit:()->Unit,delete:()->Unit) { Card(Modifier.fillMaxWidth().clickable(enabled=canWrite,onClick=edit)) { Row(Modifier.padding(12.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Column { Text(event.title); Text(if(event.allDay) "Todo el día" else event.startAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))); if(event.participantNames.isNotEmpty()) Text(event.participantNames.joinToString()) }; TextButton(enabled=canWrite,onClick=delete){Text("Eliminar")} } } }

@Composable private fun EventEditor(event:CalendarEvent?,day:LocalDate,members:List<SpaceMember>,saving:Boolean,dismiss:()->Unit,save:(String?,EventInput)->Unit) {
    val zone=ZoneId.systemDefault(); var title by remember { mutableStateOf(event?.title.orEmpty()) }; var description by remember { mutableStateOf(event?.description.orEmpty()) }; var allDay by remember { mutableStateOf(event?.allDay ?: true) }
    var start by remember { mutableStateOf(event?.let { if(it.allDay) it.startDate.toString() else it.startAt.atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) } ?: day.toString()) }
    var end by remember { mutableStateOf(event?.let { if(it.allDay) it.endDateExclusive!!.minusDays(1).toString() else it.endAt.atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) } ?: day.toString()) }; var error by remember { mutableStateOf<String?>(null) }; var color by remember { mutableStateOf(event?.color?:EventColor.BLUE) }; var selected by remember { mutableStateOf(event?.participantIds?.toSet().orEmpty()) }
    AlertDialog(onDismissRequest=dismiss,title={Text(if(event==null)"Nuevo evento" else "Editar evento")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){ OutlinedTextField(title,{title=it},label={Text("Título")}); OutlinedTextField(description,{description=it},label={Text("Descripción")}); Row{Checkbox(allDay,{allDay=it});Text("Todo el día")}; OutlinedTextField(start,{start=it},label={Text(if(allDay)"Inicio AAAA-MM-DD" else "Inicio AAAA-MM-DD HH:mm")}); OutlinedTextField(end,{end=it},label={Text(if(allDay)"Fin AAAA-MM-DD" else "Fin AAAA-MM-DD HH:mm")}); Text("Color"); Row { EventColor.entries.forEach { Text(it.name.take(1),Modifier.clickable { color=it }.padding(5.dp),color=if(it==color)MaterialTheme.colorScheme.primary else LocalContentColor.current) } }; if(members.isNotEmpty()) Text("Participantes opcionales"); members.forEach { member -> Row { Checkbox(member.userId in selected,{ checked -> selected=if(checked)selected+member.userId else selected-member.userId }); Text(member.displayName) } }; error?.let{Text(it,color=MaterialTheme.colorScheme.error)} }},confirmButton={Button(enabled=!saving,onClick={try { val formatter=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"); val sd=if(allDay)LocalDate.parse(start) else null; val ed=if(allDay)LocalDate.parse(end).plusDays(1) else null; val si=if(allDay)sd!!.atStartOfDay(zone).toInstant() else parseLocal(start,formatter,zone); val ei=if(allDay)ed!!.atStartOfDay(zone).toInstant() else parseLocal(end,formatter,zone); save(event?.id,EventInput(title,description,allDay,si,ei,sd,ed,zone.id,color,selected.toList())) }catch(e:Exception){error="Revisa las fechas y el intervalo"}}){Text("Guardar")}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})
}
private fun parseLocal(value:String, formatter:DateTimeFormatter, zone:ZoneId):Instant { val local=LocalDateTime.parse(value,formatter); val offsets=zone.rules.getValidOffsets(local); require(offsets.isNotEmpty()); return local.atOffset(offsets.first()).toInstant() }
