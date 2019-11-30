
/** ----------------------- Superblock.java --------------------------
 * @author Connor Riley Shabro
 * @author Jeffrey Murray Jr
 * @author Camila Valdebenito
// *
// * DESCRIPTION:
// * This class
// * 
// *  
// * ASSUMPTIONS: Assumes that the user has access to ThreadOS

*/

class Superblock {
   public int totalBlocks; // the number of disk blocks
   public int totalInodes; // the number of inodes
   public int freeList;    // the block number of the free list's head
   private byte[] superBlock;
   private int diskSize;

   public SuperBlock( int diskSize ) {
	    superBlock = new byte[diskSize];
      this.diskSize = diskSize;
      SysLib.rawread(0, superBlock);
      freeList = SysLib.bytes2int(superBlock, 8);
      totalBlocks = SysLib.bytes2int(superBlock, 0);
      totalInodes = SysLib.bytes2int(superBlock, 4);
   }

   public int nextBlock(){
     if(freeList > 0){
       superBlock = new byte[diskSize];
       SysLib.rawread(freeList, superBlock);

       int temp = freeList;
       freeList = SysLib.bytes2int(superBlock, 0);
       return temp;
     }
     return -1;
   }

   public synchronized boolean addBlock(int block){
    if(block > 0 && block < diskSize){
      int list = freeList;
      int temp = 0;
      byte[] next = new byte[diskSize];
      byte[] newBlock = new byte[diskSize];

      SysLib.int2bytes(-1, newBlock, 0);
      while(list != -1){
        SysLib.rawread(list, next);
        temp = SysLib.bytes2int(next, 0);
        if(temp == -1){
          SysLib.int2bytes(block, next, 0);
          SysLib.rawwrite(list, next);
          SysLib.rawwrite(block, newBlock);
          return true;
        }
        list = temp;
      }
    }
   }
   return false;
}
