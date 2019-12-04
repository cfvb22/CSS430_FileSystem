/** ========================================== SuperBlock.java ==============================================
 * @author Connor Riley Shabro
 * @author Jeffrey Murray Jr
 * @author Camila Valdebenito
 */
public class SuperBlock {
	public int totalBlocks; // total number of disk blocks
  public int totalInodes; // total number of inodes
  public int freeList;    // free list's head
	public int blockSize;   //This is the size of the blocks

	//Constructor that sets the amount of blocks
	public SuperBlock(int numBlocks){
		//Creates a new superblock and reads to it from the disk
		blockSize = Disk.blockSize;
		byte [] superBlock = new byte[blockSize];
		SysLib.rawread(0, superBlock);

		//Sets the totalBlocks, totalInodes, and freeList from the superBlock
		//that was read to before this.
		totalBlocks = SysLib.bytes2int(superBlock, 0);
		totalInodes = SysLib.bytes2int(superBlock, 4);
		freeList = SysLib.bytes2int(superBlock, 8);

		//If the SuperBlock was created correctly it returns, Otherwise
		//it formats with 64 since that is the assumed default.
		if (totalInodes > 0 && freeList >= 2 && totalBlocks == numBlocks){}
		else{
			totalBlocks = numBlocks;
			format(64);
		}
	}

	//Syncs the SuperBlock variables with the disk
	public void sync (){
		//Creates an empty byte array to be written to disk
		byte[] temp = new byte[blockSize];

		//Syncs the SuperBlock variables using the array
		SysLib.int2bytes(freeList, temp, 8);
		SysLib.int2bytes(totalInodes, temp, 4);
		SysLib.int2bytes(totalBlocks, temp, 0);

		//Writes the array to disk
		SysLib.rawwrite(0, temp);
	}


	public int nextBlock(){
		//If the freeList is still within the bounds we can
		//get the new ID for the next block
		if (freeList > 0 && freeList < totalBlocks){

			//Creates temp array
			byte[] tempArray = new byte[blockSize];
			//Reads data
			SysLib.rawread(freeList, tempArray);

			//Creates temp variable so that freeList can be updated
			int tempList = freeList;

			// Updates the freeList
			freeList = SysLib.bytes2int(tempArray, 0);

			//Returns the Block info
			return tempList;
		}
		//returns -1 if failed
		return -1;
	}

	//Attempts to return block to freeList based on ID given
	public boolean returnBlock(int blockNumber){
		//Only runs if block is within range
		if (blockNumber > 0 && blockNumber < totalBlocks){
			//newBlock is the block being added
			byte [] newBlock = new byte[blockSize];

			//updates the freeList with the newBlock and then writes the
			//block and returns true
			SysLib.int2bytes(freeList, newBlock, 0);
			freeList = blockNumber;
			SysLib.rawwrite(blockNumber, newBlock);
			return true;
		}
		//If it didn't work it returns false
		return false;
	}

	//this makes sure the superblock matches the format of the disk
  public void format (int numberOfFiles){
		//updates the totalInodes based on the amount of files
		totalInodes = numberOfFiles;

		//Sets all these Inodes to disks using the default constructor
		for (short i = 0; i < totalInodes; i++){
			Inode temp = new Inode();
			temp.toDisk(i);
		}

		//updates freeList to be the correct number
		freeList = 2 + (totalInodes / 16);

		//Creates new byte array that will be used to format
		byte[] tempArray = new byte[blockSize];

		//runs through the freeList blocks and writes to the disk
		for (int i = freeList; i < 1000 - 1; i++){
			tempArray = new byte[blockSize];
			SysLib.int2bytes(i + 1, tempArray, 0);
			SysLib.rawwrite(i, tempArray);
		}

		//makes tempArray empty again
		tempArray = new byte[blockSize];

		//resets all of SuperBlocks variables
		SysLib.int2bytes(totalBlocks, tempArray, 0);
		SysLib.int2bytes(totalInodes, tempArray, 4);
		SysLib.int2bytes(freeList, tempArray, 8);

		//Writes the new SuperBlock to the disk
		SysLib.rawwrite(0, tempArray);
    }
}
