/** ========================================== FileSystem.java ==============================================
 * @author Camila Valdebenito
 * @author Connor Riley Shabro
 * @author Jeffrey Murray Jr
 *
 * PURPOSE
 * Performs all of the operations on disk.
 * Interface for users, providing a list of operations they can use
 * Called by SysLib interface -> Kernel handles request -> FileSystem executes
 *
 * USERS CAN
 * 	format
 * 	open
 * 	write
 * 	read
 * 	delete
 * 	seek
 * 	close
 *
 */
public class FileSystem {

    private SuperBlock superblock;
    private Directory directory;
    private FileTable filetable;

	 //---------------------- FileSystem( int ) ---------------------
    /**
	 *! Default Constructor
	 * Creates superblock, directory, and file table. Stores file table in directory.
	 * @param blocks
	 */
    public FileSystem (int blocks)
    {
      //Creates the superBlock, directory, and filetable
    	superblock = new SuperBlock(blocks);
    	directory = new Directory(superblock.totalInodes);
    	filetable = new FileTable(directory);

    	//opens the root
    	FileTableEntry ftEnt = open( "/", "r");
      //gets size of root
    	int size = fsize(ftEnt);

      //if root is larger than 0 it makes root the new directory
    	if (size > 0){
    		byte[] data = new byte[size];
    		read(ftEnt, data);
    		directory.bytes2directory(data);
    	}
      //closes the root
    	close(ftEnt);
    }

	 //---------------------- int sync( ) ---------------------
	/**
	 * Syncs the file system back to the physical disk.
	 * Write the directory info to the disk in byte form in the root directory
	 * @see SuperBlock.java
	 */
    public void sync(){
    	//Opens root
    	FileTableEntry root = open("/", "w");
        //writes the directory to root
    	write(root, directory.directory2bytes());
        //closes the root
    	close(root);
        //Syncs the superBlock
    	superblock.sync();
    }

	 //---------------------- int format( int ) ---------------------
	/**
	 * Full format of the disk, erases all the content on the disk.
	 * Reinitalizes the superblock, directory, and file tables.
	 *! This operation is not reversible
	 * @param files amount of files being formatted
	 * @return success always
	 */
    public boolean format( int files){
        //Formats SuperBlock
    	superblock.format(files);
        //creates new directory based on the amount of Inodes
    	directory = new Directory(superblock.totalInodes);
        //creates a new file table based on the new directory
    	filetable = new FileTable(directory);
      //returns true
      return true;
	}

	 //---------------------- int open( FileTableEntry, String ) ---------------------
	/**
	 * @param filename name of file opening
	 * @param mode purpose of open
	 * @return the file table entry opened
	 */
    public FileTableEntry open(String filename, String mode){
    	// falloc will return a FTE with either r/w
    	FileTableEntry ftEntry = filetable.falloc(filename, mode);
        //sees if mode is write
    	  if (mode == "w"){
    		//unallocate all blocks
    		if (deallocEntry( ftEntry ) == false){
    			return null;
    		}
    	}
    	return ftEntry;
    }
	 //---------------------- int close( FileTableEntry ) ---------------------
	/**
	 * Closes the file the given file table entry.
	 * @param entry table entry to close
	 * @return freed status or true
	 */
    public boolean close(FileTableEntry ftEnt){
    	//synchronized so threads don't overlap
    	synchronized(ftEnt) {
			//removes the amount of people with file open
			ftEnt.count--;

      //If no threads are using file anymore free it from filetable
			if (ftEnt.count == 0) {
				return filetable.ffree(ftEnt);
			}
      //Otherwise just return true
			return true;
		}
	}

	//---------------------- int read( FileTableEntry, byte[] ) ---------------------
	/**
	 * Checks target block to make sure it is valid to read from
	 * @param entry table entry reading from
	 * @param buffer size of data being read
	 * @return amount of data read
	 */
	public int read(FileTableEntry ftEnt, byte[] buffer){
    //If the file was not meant to be read return a fail -1
		if (ftEnt.mode == "w" || ftEnt.mode == "a")
			return -1;

    //Creates a size, bytesRead, and bytesLeft to keep track of what you're reading
    int size  = buffer.length;
    int bytesRead = 0;
    int bytesLeft = 0;

    //synchronized so threads don't overlap
    synchronized(ftEnt){
      //While the seekPointer is not greater than filesize and it isn't size 0 or less
      while (ftEnt.seekPtr < fsize(ftEnt) && size > 0){
        //Finds the block the file is in
        int currentBlock = ftEnt.inode.fetchTarget(ftEnt.seekPtr);
        //If error with block it stops looping
        if (currentBlock == -1)
        	break;

				//cur is the data you are going to be reading
				byte[] cur = new byte[Disk.blockSize];
        //reads data into cur
        SysLib.rawread(currentBlock, cur);

				//Sets the data to make sure you read the right amount of data
        int dataOffset = ftEnt.seekPtr % Disk.blockSize;
        int blocksLeft = Disk.blockSize - bytesLeft;
        int fileLeft = fsize(ftEnt) - ftEnt.seekPtr;

				//if less blocks less then bytes is = to blocksLeft
        //otherwise bytes left is = to how much file is left
        if (blocksLeft < fileLeft)
					bytesLeft = blocksLeft;
				else
					bytesLeft = fileLeft;

        //This checks to see if the bytes left is greater than the size
        //If so it gets changed to that
				if (bytesLeft > size)
					bytesLeft = size;

				//Uses array copy with the updated variables
        System.arraycopy(cur, dataOffset, buffer, bytesRead, bytesLeft);
        bytesRead += bytesLeft;
        ftEnt.seekPtr += bytesLeft;
        size -= bytesLeft;
      }
      //returns the amount of bytes that were read
      return bytesRead;
    }
	}

	//---------------------- int write( FileTableEntry, byte[] ) ---------------------
	/**
	 * Writes the contents of buffer to the file indicated by entry.
	 * Increments the seek pointer by the number of bytes to have been written.
	 * @param entry file table entry writing to
	 * @param buffer contents to be written
	 * @return number of bytes written, -1 if failure
	 */
  public int write(FileTableEntry ftEnt, byte[] buffer){
    //This keeps track the amount of bytes being written and how larger the buffer is
    int bytesWritten = 0;
		int bufferSize = buffer.length;
		int blockSize = Disk.blockSize;

    //If the file is null or shouldn't write it returns a fail -1
		if (ftEnt == null || ftEnt.mode == "r"){
			return -1;
		}

    //synchronized so threads don't run over eachother
		synchronized (ftEnt){
      //while there is stuff to still be written
			while (bufferSize > 0){
        //Where you write
				int location = ftEnt.inode.fetchTarget(ftEnt.seekPtr);

				//If the location can't be written to it finds a new block
				if (location == -1)
					location = assignLocation(ftEnt);

				//Creates a temp buffer and reads from the location
				byte[] tempBuff = new byte[blockSize];
				SysLib.rawread(location, tempBuff);

        //creates a temp pointer and the difference between blockSize and ptr
				int tempPtr = ftEnt.seekPtr % blockSize;
				int diff = blockSize - tempPtr;

				//if the difference is less than the buffer size
				if (diff > bufferSize){
          //copies the array using the variables and then writes the data
					System.arraycopy(buffer, bytesWritten, tempBuff, tempPtr, bufferSize);
					SysLib.rawwrite(location, tempBuff);

          //updates variables after writing using the buffersize
					ftEnt.seekPtr += bufferSize;
					bytesWritten += bufferSize;
					bufferSize = 0;
				}
				else {
          //copies the array using the variables and then writes the data
					System.arraycopy(buffer, bytesWritten, tempBuff, tempPtr, diff);
					SysLib.rawwrite(location, tempBuff);

          //updates the variables after writing using the difference
					ftEnt.seekPtr += diff;
					bytesWritten += diff;
					bufferSize -= diff;
				}
			}

      ///if the pointer is larger than the Inode it updates the length of the Inode
			if (ftEnt.seekPtr > ftEnt.inode.length){
				ftEnt.inode.length = ftEnt.seekPtr;
			}
      //puts the Inode to the disk
			ftEnt.inode.toDisk(ftEnt.iNumber);
      //returns the amount of bytes that were written
			return bytesWritten;
		}
	}

	//---------------------- int assignLocation( FileTableEntry ) ---------------------
	/**
	 * Helper function for handling iNode return values
	 * Assumption: location == -1
	 * @return newLocation
	 */
	private int assignLocation(FileTableEntry ftEnt){
    //the new location is wherever the next superblock finds
		short newLocation = (short) superblock.nextBlock();

    //The testpointer is the free index it can find
		int testPtr = ftEnt.inode.getFreeBlockIndex(ftEnt.seekPtr, newLocation);

		//If the pointer is incorrect it finds a new block
		if (testPtr == -3){
			short freeBlock = (short) this.superblock.nextBlock();

			//If the second location is bad it returns -1
			if (ftEnt.inode.setIndexBlock(freeBlock) == false)
				return -1;

			//If the new location index is wrongit returns -1
			if (ftEnt.inode.getFreeBlockIndex(ftEnt.seekPtr, newLocation) != 0)
				return -1;
		}
		//Error for testpointers below 0
		else if (testPtr == -2 || testPtr == -1)
			return -1;

		return newLocation;
	}


	//---------------------- seek( FileTableEntry, int, int ) ---------------------
	private final int SEEK_SET = 0;
	private final int SEEK_CUR = 1;
	private final int SEEK_END = 2;

	// Updates the seek pointer corresponding to fd as follows:
	// If whence is SEEK_SET (= 0), the file's seek pointer is set to offset bytes from the beginning of the file.
	// If whence is SEEK_CUR (= 1), the file's seek pointer is set to its current value plus the offset. The offset can be positive or negative.
	// If whence is SEEK_END (= 2), the file's seek pointer is set to the size of the file plus the offset. The offset can be positive or negative.
	public synchronized int seek(FileTableEntry ftEnt, int offset, int whence)
	{
	   switch(whence)
	   {
		  // file's seek pointer is set to offset bytes from the beginning of the file
		  case SEEK_SET:
			 ftEnt.seekPtr = offset;
			 break;

		  // file's seek pointer is set to its current value plus the offset
		  case SEEK_CUR:
			 ftEnt.seekPtr += offset;
			 break;

		  // file's seek pointer is set to the size of the file plus the offset
		  case SEEK_END:
			 ftEnt.seekPtr = offset + fsize(ftEnt);
			 break;

		  default:
			 return -1;
	   }

	   if(ftEnt.seekPtr < 0)
	   {
		  ftEnt.seekPtr = 0;
	   }
	   else if (ftEnt.seekPtr > fsize(ftEnt))
	   {
		  ftEnt.seekPtr = fsize(ftEnt);

	   }

	   return ftEnt.seekPtr;
 }
 	 //---------------------- boolean deallocEntry( FileTableEntry ) ---------------------
	/**
	 * Iterates through direct and indirect of given FileTableEntry
	 * Checks all values are valid, sets them to invalid, then returns them to superblock.
	 * @param ftEnt entry deallocating
	 * @return successful/fail
	 */
    private boolean deallocEntry(FileTableEntry ftEnt){
      //If it has more than one Inode it returns
    	if (ftEnt.inode.count != 1){
			return false;
		}

    //Goes through the blockIds returning them to the Freelist in superBlock
		for (short blockId = 0; blockId < ftEnt.inode.directSize; blockId++){
			if (ftEnt.inode.direct[blockId] != -1){
				superblock.returnBlock(blockId);
				ftEnt.inode.direct[blockId] = -1;
			}
		}

    //data is the free indirect
		byte[] data = ftEnt.inode.freeIndirect();
		if (data != null){
      //returns all the indirect blocks to superBlock
			short blockId;
			while((blockId = SysLib.bytes2short(data, 0)) != -1){
				superblock.returnBlock(blockId);
			}
		}
    //puts inode on the disk
		ftEnt.inode.toDisk(ftEnt.iNumber);
		return true;
    }

	 //---------------------- boolean delete( String ) ---------------------
	/** Deletes the file specified by given fileName.
	 * If the file is currently open, it is not destroyed
	 * until the last open on it is closed, but new attempts to open it will fail.
	 */
	public boolean delete(String filename){
    //opens file
		FileTableEntry tcb = open(filename, "w");

    //frees the inode and closes
		if(directory.ifree(tcb.iNumber) && close(tcb))
			return true;

    //If not deleted returns false
		return false;
	}


	//---------------------- int fsize( FileTableEntry )---------------------
   /**
	* @param ftEnt: this the the FileTableEntry it will find the size of
	* @return length of FileTableEntry
	*/
	public int fsize(FileTableEntry ftEnt){
	//if it is null return -1
		if(ftEnt == null)
			return -1;

	// synchronized so that threads don't screw up the FileTableEntry
		synchronized(ftEnt) {
			return ftEnt.inode.length;
		}
	}
}
