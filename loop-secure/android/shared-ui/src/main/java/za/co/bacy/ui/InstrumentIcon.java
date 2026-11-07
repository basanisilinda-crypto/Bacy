package za.co.bacy.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Familiar line icons drawn locally so every supported phone has the same navigation. */
public final class InstrumentIcon extends View {
    private final String name;
    private final int color;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public InstrumentIcon(Context context,String name,int color){super(context);this.name=name;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        Path p=new Path();
        switch(name){
            case "home":p.moveTo(3,10);p.lineTo(12,3);p.lineTo(21,10);p.moveTo(5,9);p.lineTo(5,21);p.lineTo(10,21);p.lineTo(10,14);p.lineTo(14,14);p.lineTo(14,21);p.lineTo(19,21);p.lineTo(19,9);canvas.drawPath(p,paint);break;
            case "payment":canvas.drawRoundRect(new RectF(2,5,22,19),3,3,paint);canvas.drawLine(2,10,22,10,paint);canvas.drawLine(6,15,10,15,paint);break;
            case "bank":p.moveTo(2,8);p.lineTo(12,3);p.lineTo(22,8);p.close();canvas.drawPath(p,paint);canvas.drawLine(3,21,21,21,paint);canvas.drawLine(4,18,20,18,paint);for(int i=6;i<=18;i+=6)canvas.drawLine(i,11,i,18,paint);break;
            case "people":canvas.drawCircle(9,8,3,paint);canvas.drawArc(new RectF(2,13,16,26),180,180,false,paint);canvas.drawArc(new RectF(14,5,20,11),-80,170,false,paint);canvas.drawArc(new RectF(13,13,23,25),-90,90,false,paint);break;
            case "phone":canvas.drawRoundRect(new RectF(6,2,18,22),3,3,paint);canvas.drawLine(10,5,14,5,paint);canvas.drawLine(11,19,13,19,paint);break;
            case "help":canvas.drawCircle(12,12,9,paint);canvas.drawArc(new RectF(9,6,15,12),-180,260,false,paint);canvas.drawLine(12,12,12,14,paint);canvas.drawPoint(12,18,paint);break;
            case "message":canvas.drawRoundRect(new RectF(3,3,21,18),4,4,paint);p.moveTo(5,17);p.lineTo(5,22);p.lineTo(11,18);canvas.drawPath(p,paint);canvas.drawLine(7,8,17,8,paint);canvas.drawLine(7,12,14,12,paint);break;
            case "settings":canvas.drawCircle(12,12,3.5f,paint);for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(12+(float)Math.cos(a)*8,12+(float)Math.sin(a)*8,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10,paint);}canvas.drawCircle(12,12,8,paint);break;
            case "shield":p.moveTo(12,2);p.lineTo(21,6);p.lineTo(20,14);p.quadTo(19,19,12,22);p.quadTo(5,19,4,14);p.lineTo(3,6);p.close();canvas.drawPath(p,paint);p.reset();p.moveTo(8,12);p.lineTo(11,15);p.lineTo(16,9);canvas.drawPath(p,paint);break;
            case "check":p.moveTo(5,12);p.lineTo(10,17);p.lineTo(20,6);canvas.drawPath(p,paint);break;
            case "chevron":p.moveTo(9,5);p.lineTo(16,12);p.lineTo(9,19);canvas.drawPath(p,paint);break;
            default:canvas.drawCircle(12,12,8,paint);
        }
        canvas.restore();
    }
}
