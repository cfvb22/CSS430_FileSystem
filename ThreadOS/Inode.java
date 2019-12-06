/** ========================================== Inode.java ==============================================
 * @author Jeffrey Murray Jr
 * @author Camila Valdebenito
 * @author Connor Riley Shabro
 * 
 * SUMMARY
 * Keeps track of which blocks are in the file (and their order)
 * Can Map an offest to a specific block
 * Direct Access to the disk itself
 */

public class Inode {
   private final static int iNodeSize = 32;       // fix to 32 bytes
   public final static int directSize = 11;      // # direct pointers

   public int length;                             // file size in bytes
   public short count;                            // # file-table entries pointing to this
   public short flag;                             // 0 = unused, 1 = used, ...
   public short direct[] = new short[directSize]; // direct pointers
   public short indirect;                         // a indirect pointer

    // ---------------------------- Constuctors ----------------------------
   /**
    *! Default
   * All values are null (-1 or 0)
   */
   Inode( ) {                                     
      length = 0;
      count = 0;
      flag = 1;
      for ( int i = 0; i < directSize; i++ )
         direct[i] = -1;
      indirect = -1;
   }

   /**
    *! Retrieve from Disk
    * Takes in iNumber and creates an iNode 
            by retrieving info from disk
    * iNode = 32 bytes (entire block)
    * start = (0-16) * 32     ex. start = 0
    * length = Block[0-3],        start = 0
    * count = Block[4-5],         start = 4
    * flag = Block[6-7],          start = 6
    * direct = Block[8-28],       start = (8 - 28)
    * indirect = Block[30-32],    start = 30
    * @param iNumber
    */
   Inode( short iNumber ) {         
      // read in disk where Inode is
      int blockNum = 1 + (iNumber / 16);
      // retrieve data
      byte[] diskBlock = new byte[Disk.blockSize];
      SysLib.rawread(blockNum, diskBlock);

      // read Inodes 32 bytes within disk block
      int start = (iNumber % 16) * iNodeSize;
      // Assign file size & increment
      length = SysLib.bytes2int(diskBlock, start);
      start += 4;
      // Assign file-table entries & increment by 4
      count = SysLib.bytes2short(diskBlock, start);
      start += 2;
      // Assign flag & increment by 2
      flag = SysLib.bytes2short(diskBlock, start);
      start += 2;

      // set (11) direct pointers & increment each iteration by 2
      for(int i = 0; i < directSize; i ++) {
         direct[i] = SysLib.bytes2short(diskBlock, start);
         start += 2;
      }
      
      // set (1) indirect pointer at Disk[30]
      indirect = SysLib.bytes2short(diskBlock, start);

   }
    // ---------------------------- toDisk ----------------------------
   /**
    *! Write to Disk 
    * Saves iNode to Disk
    * Writes to Disk as the i-th iNode
    * @param iNumber
    */
   int toDisk( short iNumber ) 
   {       
        if(iNumber < 0) return -1;

        int blockSize = 16;
        byte [] data = new byte[iNodeSize];
        int offset = 0;

        // Assign flags to byte[]
        SysLib.int2bytes(length, data, offset);
        offset += 4;
        SysLib.short2bytes(count, data, offset);
        offset += 2;
        SysLib.short2bytes(flag, data, offset);
        offset += 2;

        // convert data from direct to bytes
        for (int i = 0; i < directSize; i++){
            SysLib.short2bytes(direct[i], data, offset);
            offset += 2;
        }

        // convert data from indirect to bytes
        SysLib.short2bytes(indirect, data, offset);
        offset += 2;

        // Read current data at blockNumber
        int blockNumber = 1 + iNumber / blockSize;
        byte[] newData = new byte[Disk.blockSize];
        SysLib.rawread(blockNumber,newData);

        // Reassign offset
        offset = (iNumber % blockSize) * iNodeSize;

        // Copy over data to newData from offset to max
        System.arraycopy(data, 0, newData, offset, iNodeSize);
        SysLib.rawwrite(blockNumber,newData);

        return 0;
   }

    // ---------------------------- fetchTarget ----------------------------
   /**
    * Looks for seekPtr in iNode
    * @param seekPtr
    * @return -1 if error, otherwise the blockIndex
    */
   int fetchTarget( int seekPtr ) {
      int start = seekPtr / Disk.blockSize;
      int blockIndex = -1;
      // Within direct block bounds
      if(start < directSize && start >= 0)
         blockIndex = direct[start];
      // indirect is not null & trying to be accessed
      else if(indirect != -1)
      {
         byte[] data = new byte[Disk.blockSize];
         // Get number of blocks indirect points to
         SysLib.rawread(indirect, data);
         // Size of indirect
         int diff = start - directSize;
         // int to byte
         blockIndex = SysLib.bytes2short(data, diff * 2);
      }
      // if seek < 0 || indirect == -1 returns error (-1)
      return blockIndex;
      
   }
    // ---------------------------- addBlock ----------------------------
   /**
    * Adds a freeBlock to the iNode
    * @param freeBlock
    * @return successful/fail
    */
   boolean addBlock( short freeBlock ) {
      int id = (length / Disk.blockSize) + 1;
      // Add to direct
      if(id < directSize) 
         direct[id] = freeBlock;
      // Set as indirect
      else if(indirect == -1)
         indirect = freeBlock;
      // Add to indirect
      else 
      {
         byte[] data = freeIndirect();
         int offset = 0;
         short blockID = SysLib.bytes2short(data, offset);
         // Continues to look for empty indirect block
         while(blockID != -1)
         {
            // Indirect block is full
            if(offset >= Disk.blockSize)
               return false;
            
            // Increment through indirect block
            offset += 2;
            blockID = SysLib.bytes2short(data, offset);
         }
         // Assign indirect block with indirectData + free block
         SysLib.short2bytes(freeBlock, data, offset);
         SysLib.rawwrite(indirect, data);
      }
      return true;
   }

    // ---------------------------- setIndexBlock ----------------------------
   /** 
     * Traverse indirect and direct to find any null values
     * Otherwise, sets indirect to blockNumber
     * @param blockNumber
     * @return success/fail
     */
    boolean setIndexBlock(short blockNumber){
      // Check if direct has any null values
      for (int i = 0; i < directSize; i++) 
          if (direct[i] == -1)
              return false;

      // If indirect is null
      if (indirect != -1)
          return false;

      // Otherise, set indirect block
      indirect = blockNumber;
      byte[ ] data = new byte[Disk.blockSize];

      for(int i = 0; i < (Disk.blockSize / 2); i++){
          SysLib.short2bytes((short) -1, data, i * 2);
      }
      SysLib.rawwrite(blockNumber, data);

      return true;

  }

    // ---------------------------- getFreeBlockIndex ----------------------------
   /** 
     * Iterates through direct and indirect and read data when valid
     * @param entry
     * @param offset in block
     * @return 0 = unused,
     *        -1 = error on write to used block, 
     *        -2 = error on write to unused block,
     *        -3 = error on write to null ptr
     */
    int getFreeBlockIndex(int entry, short offset){
      int target = entry / Disk.blockSize;
    
      if (target < directSize)
      {
          // Target has a value
            if(direct[target] >= 0)
                return -1; 

          // Target - 1 is empty && target is within direct
            if ((target > 0 ) && (direct[target - 1 ] == -1))
                return -2;
          
          // Otherwise, safe to allocate space
          direct[target] = offset;
          return 0;
      }
      // Indirect is null
      if (indirect < 0)
        return -3; 

      // Otherwise, look through indirect
      else
      {
          // Read current data
          byte[] data = new byte[ Disk.blockSize ];
          SysLib.rawread(indirect,data);

          // Check for values > 0 in indirect
          int blockSpace = (target - directSize) * 2;
          if ( SysLib.bytes2short(data, blockSpace) > 0){
              return -1;
          }
          // Otherwise, free block
          else
          {
              SysLib.short2bytes(offset, data, blockSpace);
              SysLib.rawwrite(indirect, data);
          }
      }
      return 0;
  }
    // ---------------------------- freeIndirect ----------------------------
   /**
    * Fetches Indirect Data (can be larger than 1 byte)
    * If it != -1, reads from disk and sets indirect to -1
    * @return null if == -1, otherwise data from disk
    */
   byte[] freeIndirect(){
      // Not set
      if(indirect == -1) return null;
      // read and set to -1
      byte[] data = new byte[Disk.blockSize];
      SysLib.rawread(indirect, data);
      indirect = -1;
      return data;
   }
}
