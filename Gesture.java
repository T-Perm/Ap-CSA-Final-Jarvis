

public class Gesture {
    private final Type type;
    private final int x;
    private final int y;
    private final double confidence;



     public enum Type {
        NONE,
        POINT,
        LEFT_CLICK,
        RIGHT_CLICK,
        PAUSE,
        RESUME
    }
    public Gesture(Type type,int x, int y, double confidence){
        this.type = type;
        this.x = x;
        this.y = y;
        this.confidence = confidence;
    }
    public int getX(){
        return x;
    }
    public int getY(){
        return y;
    }
    public Type getType(){
        return type;
    }
    public double getConfidence(){
        return confidence;
    }

    public String toString(){
        return type + " @ " + x + "," + "Y" + "conf=" + confidence;
    }
}
