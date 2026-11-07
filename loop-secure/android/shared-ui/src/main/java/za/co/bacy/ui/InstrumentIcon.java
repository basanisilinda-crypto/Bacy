package za.co.bacy.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Compact geometric instruments; rendered locally with no font or network dependency. */
public final class InstrumentIcon extends View {
    private final String name;private final int color;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public InstrumentIcon(Context context,String name,int color){super(context);this.name=name;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/24f,getHeight()/24f);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.9f);paint.setStrokeCap(Paint.Cap.SQUARE);paint.setStrokeJoin(Paint.Join.ROUND);Path p=new Path();
        switch(name){
            case "home":paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(new RectF(3,3,10,10),1,1,paint);canvas.drawRoundRect(new RectF(13,3,21,7),1,1,paint);canvas.drawRoundRect(new RectF(3,13,10,21),1,1,paint);canvas.drawRoundRect(new RectF(13,10,21,21),1,1,paint);break;
            case "payment":canvas.drawRoundRect(new RectF(3,7,21,21),2,2,paint);canvas.drawLine(6,3,18,3,paint);paint.setStyle(Paint.Style.FILL);canvas.drawRect(3,10,21,13,paint);canvas.drawRect(6,16,10,18,paint);break;
            case "bank":p.moveTo(2,8);p.lineTo(12,2);p.lineTo(22,8);p.close();paint.setStyle(Paint.Style.FILL);canvas.drawPath(p,paint);canvas.drawRect(3,20,21,22,paint);for(int i=5;i<=17;i+=6)canvas.drawRect(i,11,i+2,18,paint);break;
            case "people":paint.setStyle(Paint.Style.FILL);canvas.drawCircle(8,6,3.5f,paint);canvas.drawCircle(18,7,2.5f,paint);canvas.drawRoundRect(new RectF(2,12,14,22),3,3,paint);canvas.drawRoundRect(new RectF(16,13,22,22),2,2,paint);break;
            case "phone":canvas.drawRoundRect(new RectF(6,2,18,22),2,2,paint);canvas.drawLine(10,5,14,5,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(12,18.5f,1,paint);break;
            case "help":canvas.drawArc(new RectF(3,3,21,21),180,180,false,paint);canvas.drawRoundRect(new RectF(3,11,7,18),1,1,paint);canvas.drawRoundRect(new RectF(17,11,21,18),1,1,paint);p.moveTo(20,18);p.lineTo(17,22);p.lineTo(12,22);canvas.drawPath(p,paint);break;
            case "message":p.moveTo(3,3);p.lineTo(21,3);p.lineTo(21,17);p.lineTo(10,17);p.lineTo(3,22);p.close();canvas.drawPath(p,paint);canvas.drawLine(7,8,17,8,paint);canvas.drawLine(7,12,13,12,paint);break;
            case "settings":for(int y=5;y<=19;y+=7)canvas.drawLine(3,y,21,y,paint);paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(new RectF(7,2,11,8),1,1,paint);canvas.drawRoundRect(new RectF(14,9,18,15),1,1,paint);canvas.drawRoundRect(new RectF(5,16,9,22),1,1,paint);break;
            case "shield":p.moveTo(12,2);p.lineTo(21,5);p.lineTo(20,15);p.lineTo(12,22);p.lineTo(4,15);p.lineTo(3,5);p.close();canvas.drawPath(p,paint);p.reset();p.moveTo(8,12);p.lineTo(11,15);p.lineTo(16,9);canvas.drawPath(p,paint);break;
            case "check":canvas.drawRoundRect(new RectF(3,3,21,21),4,4,paint);p.moveTo(7,12);p.lineTo(11,16);p.lineTo(17,8);canvas.drawPath(p,paint);break;
            case "refresh":canvas.drawArc(new RectF(4,4,20,20),-70,140,false,paint);canvas.drawArc(new RectF(4,4,20,20),110,140,false,paint);p.moveTo(15,4);p.lineTo(20,7);p.lineTo(21,2);p.moveTo(9,20);p.lineTo(4,17);p.lineTo(3,22);canvas.drawPath(p,paint);break;
            case "chevron":canvas.drawLine(4,12,20,12,paint);p.moveTo(14,6);p.lineTo(20,12);p.lineTo(14,18);canvas.drawPath(p,paint);break;
            default:canvas.drawRoundRect(new RectF(4,4,20,20),3,3,paint);
        }
        canvas.restore();
    }
}
