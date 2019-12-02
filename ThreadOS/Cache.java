// --------------------------------- Cache.java --------------------------------
/**
 * AUTHOR: Jeffrey Murray Jr
 * CSS 430 - Programming Assingment 4
 *
 * PURPOSE OF FILE
 * This file's purpose serves to implement disk caching baseed upon the approach 
 * of using an Enhanced Second Chance Algorithm (ESCA).
 */


public class Cache {

    // Singleton
    CacheBlock[] myCache; 
    
    // Reassignable values
	private int victim;								// ID of victim block
	private int target;								// Target Data Block
	private int blockSize;							// Size of curent block
    private int pageSize;							// Size of Cache Array
    
    // ERROR CODES
    private int EMPTY;      // -1
    private int INVALID;    // -2

    // -------------------------------------------------------------------------
    //! private CacheBlock 
	private class CacheBlock{
		byte[] dataBlock;
		int blockFrame;
		boolean referenceBit;
		boolean dirtyBit;

        /**
         *! private Constructor
         * @param blockSize
         */
		private CacheBlock(int blockSize){
			dataBlock = new byte[blockSize];		
			blockFrame = EMPTY;						
			referenceBit = false;					
			dirtyBit = false;						
		}
	}

    // -------------------------------------------------------------------------
    //! public Constructor 
    /**
     * Cache[] const
     * @param block_Size of each block in Cache
     * @param cacheBlock static size of Cache
     */
	public Cache(int block_Size, int cacheBlock){
		myCache = new CacheBlock[cacheBlock];		
		pageSize = myCache.length;					
		blockSize = block_Size;						
		victim = cacheBlock-1;						
		for(int i = 0; i < pageSize; i++){
			myCache[i] = new CacheBlock(block_Size);
		} 
	}

    // -------------------------------------------------------------------------
    //! public bool read 
    /**
     *  Reads into buffer[] of cache block by blockId
     *  @param blockId
     *  @param buffer
     *  @return false if invalid, otherwise true if found
     */
	public synchronized boolean read(int blockId, byte[] buffer){
    //  Invalid blockId
		if(blockId < 0){ return false; }			
	//  Look for existing blockId	
		target = find(blockId);					
		if(target != INVALID){ 						
			fromCache(target, blockId, buffer);	
			return true; 
        }
    //  Look for empty blockId
		target = find(EMPTY);					
		if(target != INVALID){ 						
			SysLib.rawread(blockId, myCache[target].dataBlock); 
			fromCache(target, blockId, buffer);	
			return true;
        }
    //  Otherwise, find next victim		
		toDisk(nextVictim());					
		SysLib.rawread(blockId, myCache[victim].dataBlock);	 
       	fromCache(victim, blockId, buffer);		    	
		return true;
	}

    // -------------------------------------------------------------------------
    //! public boolean write 
    /**
     *  Writes to buffer[] of cache at blockId 
     *  @param blockId
     *  @param buffer
     *  @return true if added, otherwise false if invalid
     */
	public synchronized boolean write(int blockId, byte[] buffer){
    //  Invalid blockId
		if(blockId < 0){ return false; }			
    //  Look for existing blockId
		target = find(blockId);					
		if(target != INVALID){ 						
			toCache(target, blockId, buffer);	
			return true; 
        }		
    //  Look for empty blockFrame
		target = find(EMPTY);					
		if(target != INVALID){ 						
			toCache(target, blockId, buffer);	
			return true; 
        }		
    //  Otherwise, find victim
		toDisk(nextVictim());					
		toCache(victim, blockId, buffer);		
		return true;								
	}

    // -------------------------------------------------------------------------
    //! public sync 
    /**
     *  Maintains clean block copies.
     */	
	public synchronized void sync(){
		for(int i = 0; i < pageSize; i++) { toDisk(i); }
		SysLib.sync();								
	}

    // -------------------------------------------------------------------------
    // flush 
    /**
     *  Invalidating all cached blocks
     */	
	public synchronized void flush(){
		for(int i = 0; i < pageSize; i++){			
			toDisk(i);							
			updateCache(i, EMPTY, false);			
		}	
		SysLib.sync();								
	}
    // -------------------------------------------------------------------------
    //! private int find 
    /**
     *  Reduces repetativeness and locates prospective value
     *  @param prospect compared to blockFrame (int)
     *  @return index or -2 if not found
     */	
	private int find(int prospect){
		for(int i = 0; i < pageSize; i++){			
			if(myCache[i].blockFrame == prospect){	
				return i;							
			}
		}
		return INVALID;								
	}

    // -------------------------------------------------------------------------
    //! public toCache 
    /**
     *? Read and Write
     *  Copies buffer[] to cache, sets as dirtyBit, updates as referenced
     *  @param target 
     *  @param blockId
     *  @param buffer
     */
	private void toCache(int target, int blockId, byte[] buffer){
		System.arraycopy(buffer, 0, myCache[target].dataBlock, 0, blockSize);
		myCache[target].dirtyBit = true;			
		updateCache(target, blockId, true);
	}

    // -------------------------------------------------------------------------
    //! public fromCache 
    /**
     *? Read Only
     *  Copies buffer[] to cache, then updates as referenced
     *  @param target
     *  @param blockId
     *  @param buffer
     */
	private void fromCache(int target, int blockId, byte[] buffer){
		System.arraycopy(myCache[target].dataBlock, 0, buffer, 0, blockSize);
		updateCache(target, blockId, true);	
	}

    // -------------------------------------------------------------------------
    //! updateCache 
    /**
     *  Marks a cacheBlock as referenced so it isn't removed by ESCA
     *  @param target
     *  @param frame
     *  @param boolVal
     */
	private void updateCache(int target, int frame, boolean boolVal){
		myCache[target].blockFrame = frame;				
		myCache[target].referenceBit = boolVal;			
	}

    // -------------------------------------------------------------------------
    //! toDisk 
    /**
     *  Writes data to disk before removed by cache
     *  @param target 
     */
	private void toDisk(int target){
        // Check for dirtyBit && !EMPTY
		if(myCache[target].dirtyBit && myCache[target].blockFrame != EMPTY){
			SysLib.rawwrite(myCache[target].blockFrame, myCache[target].dataBlock);
			myCache[target].dirtyBit = false;			
		}
	}

    // -------------------------------------------------------------------------
    //! private int nextVictim 
    /**
     *? Second Chance Algorithm 
     *  Loops through cache until finds qualified victim ID
     *  Qualified: hasn't been recently used
     *  Sets referencedBit to false, giving block second chance
     *  @return new victim
     */
	private int nextVictim(){
		while(true){									
			victim = ((++victim) % pageSize);			
			if(!myCache[victim].referenceBit){ return victim; } 
			myCache[victim].referenceBit = false; 	
		}
	}	
}
