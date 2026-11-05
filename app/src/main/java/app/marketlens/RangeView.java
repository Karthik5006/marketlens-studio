package app.marketlens;
import android.animation.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
final class RangeView extends View {
 private final Paint paint=new Paint(3);private final double position;private float progress;private ValueAnimator animator;
 RangeView(Context c,double value,double low,double high){super(c);position=Data.position(value,low,high);setContentDescription(Double.isFinite(position)?"Price is "+Math.round(position*100)+" percent of the way from the 52-week low to high":"52-week range unavailable");}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();animator=ValueAnimator.ofFloat(0,1);animator.setDuration(650);animator.addUpdateListener(v->{progress=(Float)v.getAnimatedValue();invalidate();});animator.start();}
 @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
 @Override protected void onDraw(Canvas c){float d=getResources().getDisplayMetrics().density,l=8*d,r=getWidth()-8*d,y=getHeight()/2f;paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(5*d);paint.setColor(0xff2c4650);c.drawLine(l,y,r,y,paint);if(Double.isFinite(position)){float x=l+(r-l)*(float)position*progress;paint.setColor(0xffa8e4cc);c.drawLine(l,y,x,y,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,8*d,paint);paint.setColor(0xff0b1b23);c.drawCircle(x,y,3*d,paint);}}
}
