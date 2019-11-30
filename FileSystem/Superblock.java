
/** ----------------------- SuperBlock.java --------------------------
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

class SuperBlock {
   public int totalBlocks; // the number of disk blocks
   public int totalInodes; // the number of inodes
   public int freeList;    // the block number of the free list's head
   private byte[] superBlock; //array of bytes to track all read data
   private int diskSize;  //This keeps the diskSize to be used for other methods

//---------------default_constructor----------------
//Creates the superblock and initializes its variables
//@parameters diskSize
   public SuperBlock( int diskSize ) {
      //creates a array of bytes and reads all the data for the superBlock
	    superBlock = new byte[diskSize];
      this.diskSize = diskSize;
      SysLib.rawread(0, superBlock);

      //Sets the freeList, totalBlocks, and totalInodes based on superBlock
      //data that was read
      freeList = SysLib.bytes2int(superBlock, 8);
      totalBlocks = SysLib.bytes2int(superBlock, 0);
      totalInodes = SysLib.bytes2int(superBlock, 4);
   }

//-----------------nextBlock------------------
//Finds the next free block in the free list and returns the value of import junit.framework.TestCase;
//@returns an integer
   public int nextBlock(){
     //If there is a freeList and it is smaller than the size of the SuperBlock
     //it will run the code
     if(freeList > 0 && freeList <= diskSize){
       //resets superBlock
       superBlock = new byte[diskSize];
       //reads based on the freeList to the new superBlock
       SysLib.rawread(freeList, superBlock);

       //saves the freelist before it gets modified again
       int temp = freeList;

       //writes the data back
       SysLib.rawwrite(freeList, superBlock);

       //uses bytes2Int to update freeList based on the superBlock
       freeList = SysLib.bytes2int(superBlock, 0);

       //returns temp
       return temp;
     }
     //Failed so it returns nothing
     return -1;
   }

//------------------addBlock--------------------
//adds a block back in once it is done being used and adds it to freeList
//@params block : block your inserting
//@returns: true or false
   public synchronized boolean addBlock(int block){
    //if the block is in range of the diskSize
    if(block > 0 && block < diskSize){

      //Creates new superBlock
      superBlock = new byte[diskSize];

      //Converts the freelist and writes the superBlock data based on the block
      SysLib.int2bytes(freeList, superBlock, 0);
      SysLib.rawwrite(block, superBlock);

      //If the block is before the first freeblock it changes it
      if(block < freeList){
        freeList = block;
      }

      //returns true
      return true;
    }
  //returns false
   return false;
}
