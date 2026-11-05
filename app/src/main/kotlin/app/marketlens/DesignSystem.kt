@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package app.marketlens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.*

internal val Night=Color(0xFF0C0E15)
internal val Panel=Color(0xFF171B27)
internal val Elevated=Color(0xFF222737)
internal val Lime=Color(0xFFD6FA85)
internal val Violet=Color(0xFFB3A2FF)
internal val Pink=Color(0xFFFF9DAE)
internal val Ink=Color(0xFFF2F3F7)
internal val Muted=Color(0xFF9199AF)
internal val Line=Color(0xFF2C3244)
internal fun number(n:Double,places:Int=2):String=if(!n.isFinite())"—" else NumberFormat.getNumberInstance(Locale("en","IN")).apply{minimumFractionDigits=places;maximumFractionDigits=places}.format(n)
internal fun rupees(n:Double)=if(n.isFinite())"₹${number(n)}" else "—"
internal fun percent(n:Double,signed:Boolean=false)=if(n.isFinite())(if(signed&&n>0)"+" else "")+number(n)+"%" else "—"
internal fun crore(n:Double)=if(n.isFinite())"₹${number(n/1e7)} Cr"else "—"
internal fun dateText(value:String)=value.ifBlank{"Not published"}

@Composable internal fun LensTheme(content:@Composable ()->Unit){MaterialTheme(colorScheme=darkColorScheme(primary=Lime,onPrimary=Night,secondary=Violet,tertiary=Pink,background=Night,surface=Panel,surfaceVariant=Elevated,onSurface=Ink,onSurfaceVariant=Muted,outline=Line,error=Pink),typography=Typography(),content=content)}
@Composable internal fun SectionTitle(title:String,caption:String="",action:String="",onAction:()->Unit={}){Row(Modifier.fillMaxWidth().padding(top=16.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,color=Ink,fontSize=21.sp,fontWeight=FontWeight.SemiBold);if(caption.isNotBlank())Text(caption,color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=4.dp))};if(action.isNotBlank())TextButton(onAction){Text(action,color=Lime,fontSize=12.sp)}}}
@Composable internal fun Label(text:String,color:Color=Muted){Text(text.uppercase(),color=color,fontSize=10.sp,fontWeight=FontWeight.SemiBold,letterSpacing=1.5.sp)}
@Composable internal fun LensCard(modifier:Modifier=Modifier,accent:Color?=null,content:@Composable ColumnScope.()->Unit){Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(if(accent!=null)Brush.linearGradient(listOf(accent.copy(alpha=.13f),Panel))else Brush.linearGradient(listOf(Panel,Panel))).border(1.dp,if(accent!=null)accent.copy(alpha=.20f)else Line.copy(alpha=.5f),RoundedCornerShape(24.dp)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}
@Composable internal fun Metric(label:String,value:String,color:Color=Ink,modifier:Modifier=Modifier){Column(modifier){Text(label,color=Muted,fontSize=11.sp);Text(value,color=color,fontSize=19.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=5.dp))}}
@Composable internal fun MetricPair(a:String,x:String,b:String,y:String,color:Color=Ink){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){Metric(a,x,color,Modifier.weight(1f));Metric(b,y,modifier=Modifier.weight(1f))}}
@Composable internal fun Caption(text:String,color:Color=Muted){Text(text,color=color,fontSize=12.sp,lineHeight=18.sp)}
@Composable internal fun EmptyState(title:String,text:String,icon:androidx.compose.ui.graphics.vector.ImageVector=Icons.Rounded.Insights){LensCard{Icon(icon,null,tint=Violet,modifier=Modifier.size(30.dp));Text(title,color=Ink,fontSize=19.sp,fontWeight=FontWeight.SemiBold);Caption(text)}}
@Composable internal fun Action(text:String,onClick:()->Unit,primary:Boolean=false,enabled:Boolean=true,icon:androidx.compose.ui.graphics.vector.ImageVector?=null,modifier:Modifier=Modifier){if(primary)Button(onClick,modifier.heightIn(min=48.dp),enabled=enabled,shape=RoundedCornerShape(15.dp),contentPadding=PaddingValues(horizontal=18.dp,vertical=12.dp)){if(icon!=null){Icon(icon,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp))};Text(text,fontWeight=FontWeight.SemiBold)}else FilledTonalButton(onClick,modifier.heightIn(min=48.dp),enabled=enabled,shape=RoundedCornerShape(15.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=Elevated,contentColor=Ink),contentPadding=PaddingValues(horizontal=15.dp,vertical=12.dp)){if(icon!=null){Icon(icon,null,Modifier.size(18.dp));Spacer(Modifier.width(7.dp))};Text(text,fontSize=12.sp)}}
@Composable internal fun ChoiceRow(choices:List<String>,selected:String,onSelect:(String)->Unit){Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){choices.forEach{choice->FilterChip(selected==choice,{onSelect(choice)},label={Text(choice,fontSize=12.sp)},shape=RoundedCornerShape(12.dp),colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Lime,selectedLabelColor=Night,containerColor=Panel,labelColor=Muted),border=FilterChipDefaults.filterChipBorder(enabled=true,selected=selected==choice,borderColor=Line,selectedBorderColor=Lime))}}}
@Composable internal fun SourceNote(source:String,asOf:String,retrieved:Long=0,issue:String="",open:(String)->Unit){Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Caption("As of ${dateText(asOf)}${if(retrieved>0)" · fetched "+java.text.SimpleDateFormat("dd MMM, HH:mm",Locale.ENGLISH).format(java.util.Date(retrieved))else ""}");if(issue.isNotBlank())Caption(issue,Pink);if(source.isNotBlank())TextButton({open(source)},contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=40.dp)){Icon(Icons.Rounded.OpenInNew,null,Modifier.size(13.dp));Spacer(Modifier.width(6.dp));Text("Original source",fontSize=11.sp)}}}
@Composable internal fun ErrorLine(state:LensState,key:String,retry:(()->Unit)?=null){val error=state.errors[key];if(key in state.busy){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp,color=Lime);Caption("Reading original source…")}}else if(error!=null){LensCard(accent=Pink){Caption(error,Pink);if(retry!=null)Action("Try again",retry)}}}
@Composable internal fun AllocationBar(label:String,value:Double,total:Double,color:Color=Lime,detail:String=""){val fraction=if(total>0)(value/total).toFloat().coerceIn(0f,1f)else 0f;val animated by animateFloatAsState(fraction,tween(700),label="allocation");Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Row{Text(label,color=Ink,fontSize=13.sp,modifier=Modifier.weight(1f));Text(percent(fraction*100.0),color=color,fontSize=12.sp)};Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Elevated)){Box(Modifier.fillMaxWidth(animated).fillMaxHeight().background(color,CircleShape))};if(detail.isNotEmpty())Caption(detail)}}

@Composable internal fun PriceChart(bars:List<Bar>,modifier:Modifier=Modifier,candles:Boolean=false,sma:Boolean=false){
 var selected by remember(bars){mutableIntStateOf(-1)};val progress by animateFloatAsState(if(bars.isNotEmpty())1f else 0f,tween(850),label="chart")
 if(bars.size<2){EmptyState("Chart needs more data","Refresh price history to draw the exchange series.");return}
 val positive=bars.last().close>=bars.first().close;val color=if(positive)Lime else Pink
 val minP=(if(candles)bars.minOf{if(it.low.isFinite())it.low else it.close}else bars.minOf{it.close})*.995
 val maxP=(if(candles)bars.maxOf{if(it.high.isFinite())it.high else it.close}else bars.maxOf{it.close})*1.005
 val denom=max(maxP-minP,.01);val focus=bars.getOrNull(selected)
 Column(modifier){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Caption(focus?.let{"${it.date} · ${rupees(it.close)}"}?:"${bars.first().date} → ${bars.last().date}",if(focus!=null)Ink else Muted);Caption(if(focus!=null)"Vol ${number(focus.volume,0)}"else "Drag to inspect")}
 Canvas(Modifier.fillMaxWidth().height(210.dp).padding(top=14.dp).semantics{contentDescription="Exchange price chart, ${bars.size} sessions, ${bars.first().date} to ${bars.last().date}. Last close ${bars.last().close}. Raw prices."}.pointerInput(bars){detectHorizontalDragGestures(onDragEnd={selected=-1},onDragCancel={selected=-1}){change,_->change.consume();selected=((change.position.x/size.width)*(bars.size-1)).roundToInt().coerceIn(0,bars.lastIndex)}}.pointerInput(bars){detectTapGestures(onPress={point->selected=((point.x/size.width)*(bars.size-1)).roundToInt().coerceIn(0,bars.lastIndex);tryAwaitRelease();selected=-1})}){
  val h=size.height*.78f
  fun x(i:Int)=i.toFloat()/bars.lastIndex*size.width
  fun y(p:Double)=h-((p-minP)/denom*h).toFloat()
  repeat(4){i->val y=h*i/3;drawLine(Line.copy(alpha=.65f),Offset(0f,y),Offset(size.width,y),1f)}
  val maxV=bars.maxOf{if(it.volume.isFinite())it.volume else 0.0}.coerceAtLeast(1.0)
  bars.forEachIndexed{i,b->val xx=x(i);if(b.volume.isFinite())drawLine(color.copy(alpha=.18f),Offset(xx,size.height),Offset(xx,size.height-(b.volume/maxV*size.height*.16).toFloat()),(size.width/bars.size*.65f).coerceAtLeast(1f))}
  if(candles){bars.take((bars.size*progress).toInt()).forEachIndexed{i,b->if(b.open.isFinite()&&b.low.isFinite()&&b.high.isFinite()){val c=if(b.close>=b.open)Lime else Pink;drawLine(c,Offset(x(i),y(b.low)),Offset(x(i),y(b.high)),1.2f);drawLine(c,Offset(x(i),y(b.open)),Offset(x(i),y(b.close)),(size.width/bars.size*.6f).coerceIn(1.5f,9f))}}}else{
   val path=Path();bars.take((bars.size*progress).toInt().coerceAtLeast(1)).forEachIndexed{i,b->if(i==0)path.moveTo(x(i),y(b.close))else path.lineTo(x(i),y(b.close))}
   val area=Path().apply{addPath(path);lineTo(x(((bars.size*progress).toInt()-1).coerceAtLeast(0)),h);lineTo(0f,h);close()};drawPath(area,Brush.verticalGradient(listOf(color.copy(alpha=.18f),Color.Transparent),endY=h));drawPath(path,color,style=Stroke(2.5f))
  }
  if(sma){val p=Path();var started=false;bars.indices.forEach{i->if(i>=19){val v=bars.subList(i-19,i+1).map{it.close}.average();if(!started){p.moveTo(x(i),y(v));started=true}else p.lineTo(x(i),y(v))}};drawPath(p,Violet,style=Stroke(2f))}
  if(selected>=0){val b=bars[selected];drawLine(Muted,Offset(x(selected),0f),Offset(x(selected),size.height),1f);drawCircle(color,5f,Offset(x(selected),y(b.close)));drawCircle(Night,2f,Offset(x(selected),y(b.close)))}
 };Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Caption("Low ${rupees(bars.minOf{it.low.takeIf(Double::isFinite)?:it.close})}");Caption("High ${rupees(bars.maxOf{it.high.takeIf(Double::isFinite)?:it.close})}")}}
}

internal data class FormField(val key:String,val label:String,val initial:String="",val options:List<String> =emptyList(),val number:Boolean=false,val helper:String="",val multiline:Boolean=false)
internal data class FormSpec(val title:String,val subtitle:String="",val fields:List<FormField>,val saveLabel:String="Save",val onSave:suspend (Map<String,String>)->Unit)
@Composable internal fun EditorSheet(spec:FormSpec,onDismiss:()->Unit){val values=remember(spec){spec.fields.associate{it.key to mutableStateOf(it.initial)}};var error by remember(spec){mutableStateOf("")};var busy by remember(spec){mutableStateOf(false)};val scope=rememberCoroutineScope()
 ModalBottomSheet(sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),onDismissRequest={if(!busy)onDismiss()},containerColor=Panel,dragHandle={BottomSheetDefaults.DragHandle(color=Muted)},modifier=Modifier.fillMaxHeight(.94f)){
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal=22.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
   Text(spec.title,color=Ink,fontSize=27.sp,fontWeight=FontWeight.SemiBold);if(spec.subtitle.isNotBlank())Caption(spec.subtitle)
   spec.fields.forEach{f->if(f.options.isNotEmpty()){Text(f.label,color=Muted,fontSize=12.sp);ChoiceRow(f.options,values.getValue(f.key).value){values.getValue(f.key).value=it}}else OutlinedTextField(values.getValue(f.key).value,{values.getValue(f.key).value=it},label={Text(f.label)},modifier=Modifier.fillMaxWidth(),singleLine=!f.multiline,minLines=if(f.multiline)3 else 1,shape=RoundedCornerShape(14.dp),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=if(f.number && f.key !in listOf("returnBelow","returnAbove","minMargin","minGrowth","ProfitLossForPeriod","ProfitLossBeforeTax","CashFlowsFromUsedInOperatingActivities","return","benchmarkReturn"))androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text));if(f.helper.isNotBlank())Caption(f.helper)}
   if(error.isNotBlank())Caption(error,Pink)
   Action(if(busy)"Saving…"else spec.saveLabel,{busy=true;error="";scope.launch{try{spec.onSave(values.mapValues{it.value.value.trim()});onDismiss()}catch(e:Exception){error=e.message?:"Could not save";busy=false}}},primary=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
  }
 }
}
