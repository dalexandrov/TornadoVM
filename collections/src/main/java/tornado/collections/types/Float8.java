package tornado.collections.types;

import java.nio.FloatBuffer;

import tornado.collections.api.Payload;
import tornado.collections.api.Vector;
import tornado.collections.math.TornadoMath;

/**
 * Class that represents a vector of 3x floats
 * e.g. <float,float,float>
 * @author jamesclarkson
 *
 */
@Vector
public class Float8 implements PrimitiveStorage<FloatBuffer> {
	public static final Class<Float8> TYPE = Float8.class;
	
	/**
	 * backing array
	 */
	@Payload final protected float[] storage;
	
	/**
	 * number of elements in the storage
	 */
	final private static int numElements = 8;


	protected Float8(float[] storage){
		this.storage = storage;
	}
	
	public Float8(){
		this(new float[numElements]);
	}
	
	public Float8(float s0, float s1, float s2, float s3,float s4, float s5, float s6, float s7){
		this();
		setS0(s0);
		setS1(s1);
		setS2(s2);
		setS3(s3);
		
		setS4(s4);
		setS5(s5);
		setS6(s6);
		setS7(s7);
	}
	
	public float get(int index){
		return storage[index];
	}
	
	public void set(int index, float value){
		storage[index] = value;
	}
	
	public void set(Float8 value){
		for(int i=0;i<8;i++)
			set(i,value.get(i));
	}
    
    public float getS0(){
    	return get(0);
    }
    
    public float getS1(){
    	return get(1);
    }
    
    public float getS2(){
    	return get(2);
    }
    
    public float getS3(){
    	return get(3);
    }
    
    public float getS4(){
    	return get(4);
    }
    
    public float getS5(){
    	return get(5);
    }
    
    public float getS6(){
    	return get(6);
    }
    
    public float getS7(){
    	return get(7);
    }
    
    
    public void setS0(float value){
    	set(0,value);
    }
    
    public void setS1(float value){
    	set(1,value);
    }
    
    public void setS2(float value){
    	set(2,value);
    }
    
    public void setS3(float value){
    	set(3,value);
    }
    
    public void setS4(float value){
    	set(4,value);
    }
    
    public void setS5(float value){
    	set(5,value);
    }
    
    public void setS6(float value){
    	set(6,value);
    }
    
    public void setS7(float value){
    	set(7,value);
    }
    
    public Float4 getHi(){
    	return new Float4(getS4(), getS5(),getS6(), getS7());
    }
    
    public Float4 getLo(){
    	return new Float4(getS0(), getS1(),getS2(), getS3());
    }
    
	/**
	 * Duplicates this vector
	 * @return
	 */
	public Float8 duplicate(){
		Float8 vector = new Float8();
		vector.set(this);
		return vector;
	}
       
    public String toString(String fmt){
        return String.format(fmt, getS0(),getS1(),getS2(),getS3(),getS4(),getS5(),getS6(),getS7());
   }
   
   public String toString(){
		return toString(FloatOps.fmt8);
   }
   
   protected static final Float8 loadFromArray(final float[] array, int index){
		final Float8 result = new Float8();
		for(int i=0;i<numElements;i++)
			result.set(i,array[index+i]);
		return result;
	}
	
	protected final void storeToArray(final float[] array, int index){
		for(int i=0;i<numElements;i++)
			array[index+i] = get(i);
	}

   @Override
   public void loadFromBuffer(FloatBuffer buffer) {
   	asBuffer().put(buffer);
   }

   @Override
   public FloatBuffer asBuffer() {
   	return FloatBuffer.wrap(storage);
   }

   public int size(){
   	return numElements;
   }
  
   /***
	 * Operations on Float8 vectors
	 */
	
	public static Float8 add(Float8 a, Float8 b){
		final Float8 result = new Float8();
		for(int i=0;i<numElements;i++)
			result.set(i,a.get(i) + b.get(i));
		return result;
	}
	
	public static Float8 add(Float8 a, float b){
		final Float8 result = new Float8();
		for(int i=0;i<numElements;i++)
			result.set(i,a.get(i) + b);
		return result;
	}
	
	public static void add(Float8 a, Float8 b, Float8 c){
		for(int i=0;i<numElements;i++)
			c.set(i,a.get(i) + b.get(i));
	}
	
	public static Float8 sub(Float8 a, Float8 b){
		final Float8 result = new Float8();
		for(int i=0;i<numElements;i++)
			result.set(i,a.get(i) - b.get(i));
		return result;
	}
	
	public static void sub(Float8 a, Float8 b, Float8 c){
		for(int i=0;i<numElements;i++)
			c.set(i,a.get(i) - b.get(i));
	}
	
	public static Float8 div(Float8 a, Float8 b){
		final Float8 result = new Float8();
		for(int i=0;i<numElements;i++)
			result.set(i,a.get(i) / b.get(i));
		return result;
	}
	
	public static void div(Float8 a, float value){
		for(int i=0;i<numElements;i++)
			a.set(i,a.get(i) / value);
	}
	
	public static void div(Float8 a, Float8 b, Float8 c){
		for(int i=0;i<numElements;i++)
			c.set(i,a.get(i) / b.get(i));
	}
	
	public static Float8 mult(Float8 a, Float8 b){
		final Float8 result = new Float8();
		for(int i=0;i<numElements;i++)
			result.set(i,a.get(i) * b.get(i));
		return result;
	}
	
	public static void mult(Float8 a, float value){
		for(int i=0;i<numElements;i++)
			a.set(i,a.get(i) * value);
	}
	
	public static void mult(Float8 a, Float8 b, Float8 c){
		for(int i=0;i<numElements;i++)
			c.set(i,a.get(i) + b.get(i));
	}
	
	public static Float8 min(Float8 a, Float8 b){
		final Float8 result = new Float8();
		for(int i=0;i<numElements;i++)
			result.set(i,Math.min(a.get(i) , b.get(i)));
		return result;
	}
	
	public static void min(Float8 a, Float8 b, Float8 c){
		for(int i=0;i<numElements;i++)
			c.set(i,Math.min(a.get(i) , b.get(i)));
	}
	
	public static float min(Float8 value){
		float result = Float.MAX_VALUE;
		for(int i=0;i<numElements;i++)
			result = Math.min(result, value.get(i));
		return result;
	}
	
	public static Float8 max(Float8 a, Float8 b){
		final Float8 result = new Float8();
		for(int i=0;i<numElements;i++)
			result.set(i,Math.max(a.get(i) , b.get(i)));
		return result;
	}
	
	public static void max(Float8 a, Float8 b, Float8 c){
		for(int i=0;i<numElements;i++)
			c.set(i,Math.max(a.get(i) , b.get(i)));
	}
	
	public static float max(Float8 value){
		float result = Float.MIN_VALUE;
		for(int i=0;i<numElements;i++)
			result = Math.max(result, value.get(i));
		return result;
	}
	
	public static Float8 sqrt(Float8 a){
		final Float8 result = new Float8();
		for(int i=0;i<numElements;i++)
			a.set(i,TornadoMath.sqrt(a.get(i)));
		return result;
	}
	
	public static boolean isEqual(Float8 a, Float8 b){
		return TornadoMath.isEqual(a.asBuffer().array(), b.asBuffer().array());
	}

	public static float findULPDistance(Float8 value, Float8 expected) {
			return TornadoMath.findULPDistance(value.asBuffer().array(), expected.asBuffer().array());
	}
  
   
}
