package tornado.collections.types;

import java.nio.FloatBuffer;

import tornado.collections.types.StorageFormats;


public class ImageFloat8  implements PrimitiveStorage<FloatBuffer> {
	/**
	 * backing array
	 */
	final protected float[]				storage;

	/**
	 * number of elements in the storage
	 */
	final private int			numElements;
	final private static int elementSize = 8;
	
    /**
     * Number of rows
     */
	final protected int Y;
    
    /**
     * Number of columns
     */
	final protected int X;
    
	/**
     * Storage format for matrix
     * @param height number of columns
     * @param width number of rows
     * @param data array reference which contains data
     */
    public ImageFloat8(int width, int height, float[] array){
    	storage = array;
    	X = width;
    	Y = height;
    	numElements = X*Y*elementSize;
    }
    
    /**
     * Storage format for matrix
     * @param height number of columns
     * @param width number of rows
     */
    public ImageFloat8(int width,int height){
    	this(width,height,new float[width*height*elementSize]);
    }
    
    public ImageFloat8(float[][] matrix){
    	this(matrix.length/elementSize,matrix[0].length/elementSize,StorageFormats.toRowMajor(matrix));
    }
    
    
    private final int toIndex(int x, int y){
    	return (x * elementSize) + (y * elementSize * X);
    }
    
    public Float8 get(int x){
    	return get(x,0);
    }
    
    public Float8 get(int x, int y){
    	final int offset = toIndex(x,y);
    	return Float8.loadFromArray(storage, offset);
    }
    
    public void set(int x, int y, Float8 value){
    	final int offset = toIndex(x,y);
    	value.storeToArray(storage, offset);
    }
    
    public int X(){
    	return X;
    }
    
    public int Y(){
    	return Y;
    }
    
    
    public void fill(float value){
    	for(int i=0;i<storage.length;i++)
    		storage[i] = value;
    }
 
    
    public ImageFloat8 duplicate(){
    	ImageFloat8 image = new ImageFloat8(X,Y);
    	image.set(this);
    	return image;
    }
    
    public void set(ImageFloat8 m) {
    	for(int i=0;i<storage.length;i++)
    		storage[i] = m.storage[i];
	}

    
    public String toString(String fmt){
    	 String str = "";

    	 for(int i=0;i<Y;i++){
        	 for(int j=0;j<X;j++){
        		 str += get(j,i).toString(fmt) + "\n";
        	 }
         }

         return str;
    }
    
    public String toString(){
    	String result = String.format("ImageFloat8 <%d x %d>",X,Y);
		 if(X<=4 && Y<=4)
			result += "\n" + toString(FloatOps.fmt8);
		return result;
	 }
    
    public Float8 mean(){
		final Float8 result = new Float8();
		for(int row = 0;row<Y;row++)
			for(int col = 0;col<X;col++)
				Float8.add(result, get(col,row),result);
		Float8.div(result,(float) (X*Y));
		return result;
	}
	
	public Float8 min(){
		final Float8 result = new Float8(Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE);
			
		
		for(int row = 0;row<Y;row++)
			for(int col = 0;col<X;col++)
				Float8.min(result, get(col,row),result);
		
		return result;
	}
	
	public Float8 max(){
		final Float8 result = new Float8(Float.MIN_VALUE,Float.MIN_VALUE,Float.MIN_VALUE,Float.MIN_VALUE,Float.MIN_VALUE,Float.MIN_VALUE,Float.MIN_VALUE,Float.MIN_VALUE);
		
		for(int row = 0;row<Y;row++)
			for(int col = 0;col<X;col++)
				Float8.max(result, get(col,row),result);
		
		return result;
	}
	
	public Float8 stdDev(){
		final Float8 mean = mean();
		final Float8 varience = new Float8();
		for(int row = 0;row<Y;row++){
			for(int col = 0;col<X;col++){
				final Float8 v = Float8.sub(mean, get(col,row));
				Float8.mult(v,v,v);
				Float8.div(v, (float) X);
			Float8.add (v , varience, varience);

			}
		}
		Float8.sqrt(varience);
		return varience;
	}
	
	public String summerise(){
		return String.format("ImageFloat3<%dx%d>: min=%s, max=%s, mean=%s, sd=%s",X,Y,min(),max(),mean(),stdDev());
	}

    @Override
  	public void loadFromBuffer(FloatBuffer buffer) {
  		asBuffer().put(buffer);
  	}

  	@Override
  	public FloatBuffer asBuffer() {
  		return FloatBuffer.wrap(storage);
  	}

  	@Override
  	public int size() {
  		return numElements;
  	}
  	
  	public FloatingPointError calculateULP(ImageFloat8 ref){
		float maxULP = Float.MIN_VALUE;
		float minULP = Float.MAX_VALUE;
		float averageULP = 0f;
		
		/*
		 * check to make sure dimensions match
		 */
		if(ref.X != X && ref.Y != Y){
			return new FloatingPointError(-1f,0f,0f,0f);
		}

		for(int j=0;j<Y;j++){
			for(int i=0;i<X;i++){
				final Float8 v = get(i, j);
				final Float8 r = ref.get(i, j);
				
				final float ulpFactor = Float8.findULPDistance(v, r);
				averageULP += ulpFactor;
				minULP = Math.min(ulpFactor, minULP);
				maxULP = Math.max(ulpFactor, maxULP);
				
			}
		}
		
		averageULP /= (float) X * Y;

		return new FloatingPointError(averageULP, minULP, maxULP, -1f);
	}
}
