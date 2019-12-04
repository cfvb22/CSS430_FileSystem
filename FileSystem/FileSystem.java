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
    	superblock = new SuperBlock(blocks);

    	directory = new Directory(superblock.totalInodes);
    	filetable = new FileTable(directory);

    	// read root
    	FileTableEntry entry = open( "/", "r");
    	int size = fsize( entry );
    	if ( size > 0 )
    	{
    		// read and convert to directory
    		byte[] data = new byte[size];
    		read( entry, data );
    		directory.bytes2directory(data);
    	}
    	close( entry );
    }

	 //---------------------- int sync( ) ---------------------
	/**
	 * Syncs the file system back to the physical disk.
	 * Write the directory info to the disk in byte form in the root directory
	 * @see SuperBlock.java
	 */
    public void sync()
    {
    	byte[] tempData = directory.directory2bytes();
    	// open root dir with write access
    	FileTableEntry root = open("/", "w");
        // write directory to root
    	write(root, directory.directory2bytes());
        // close root directory
    	close(root);
        // sync superblock
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
        // format superblock for number of files
    	superblock.format(files);
        // New directory, and register root "/"
    	directory = new Directory(superblock.totalInodes);
        // New File Table with new directory
    	filetable = new FileTable(directory);
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
		// r -> found target file
		// w -> new file with filename
    	FileTableEntry ftEntry = filetable.falloc(filename, mode);
        // write check
    	if (mode == "w")
    	{
    		// if so, make sure all blocks are unallocated
    		if (deallocEntry( ftEntry ) == false)
    		{
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
    public boolean close(FileTableEntry entry){
    	// entry must be synchronized
    	synchronized(entry) {
			// decrease the number of users
			entry.count--;

			if (entry.count == 0) {
				return filetable.ffree(entry);
			}
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
	public int read(FileTableEntry entry, byte[] buffer)
	{
        //check write or append status
		if ((entry.mode == "w") || (entry.mode == "a"))
			return -1;

        int size  = buffer.length;   //set total size of data to read
        int bytesRead = 0;            //track data read
        int bytesLeft = 0;

        synchronized(entry)
        {
        	while (entry.seekPtr < fsize(entry) && (size > 0))
        	{
        		int currentBlock = entry.inode.fetchTarget(entry.seekPtr);
        		if (currentBlock == -1)
        			break;

				// read current data
				byte[] data = new byte[Disk.blockSize];
        		SysLib.rawread(currentBlock, data);

				// intialize iterative values
        		int dataOffset = entry.seekPtr % Disk.blockSize;
        		int blocksLeft = Disk.blockSize - bytesLeft;
        		int fileLeft = fsize(entry) - entry.seekPtr;

				// Assign blocks left to read
        		if (blocksLeft < fileLeft)
					bytesLeft = blocksLeft;
				else
					bytesLeft = fileLeft;

				if (bytesLeft > size)
					bytesLeft = size;

				// Copy data & adjust iteratives
        		System.arraycopy(data, dataOffset, buffer, bytesRead, bytesLeft);
        		bytesRead += bytesLeft;
        		entry.seekPtr += bytesLeft;
        		size -= bytesLeft;
        	}
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
    public int write(FileTableEntry entry, byte[] buffer){
    	int bytesWritten = 0;
		int bufferSize = buffer.length;
		int blockSize = Disk.blockSize;

		if (entry == null || entry.mode == "r")
		{
			return -1;
		}

		synchronized (entry)
		{
			while (bufferSize > 0)
			{
				int location = entry.inode.fetchTarget(entry.seekPtr);

				// if current block null
				if (location == -1)
					location = assignLocation(entry);

				// assign a buffer & read at location
				byte [] tempBuff = new byte[blockSize];
				SysLib.rawread(location, tempBuff);

				int tempPtr = entry.seekPtr % blockSize;
				int diff = blockSize - tempPtr;

				// Rainy Day, writing the final bits, exit
				if (diff > bufferSize)
				{
					System.arraycopy(buffer, bytesWritten, tempBuff, tempPtr, bufferSize);
					SysLib.rawwrite(location, tempBuff);

					entry.seekPtr += bufferSize;
					bytesWritten += bufferSize;
					bufferSize = 0;
				}
				// Sunny Day, writing bits, continue
				else {
					System.arraycopy(buffer, bytesWritten, tempBuff, tempPtr, diff);
					SysLib.rawwrite(location, tempBuff);

					entry.seekPtr += diff;
					bytesWritten += diff;
					bufferSize -= diff;
				}
			}

			// update inode length if seekPtr larger

			if (entry.seekPtr > entry.inode.length)
			{
				entry.inode.length = entry.seekPtr;
			}
			entry.inode.toDisk(entry.iNumber);
			return bytesWritten;
		}
	}

	//---------------------- int assignLocation( FileTableEntry ) ---------------------
	/**
	 * Helper function for handling iNode return values
	 * Assumption: location == -1
	 * @return newLocation
	 */
	private int assignLocation(FileTableEntry ftEnt)
	{
		short newLocation = (short) superblock.nextBlock();

		int testPtr = ftEnt.inode.getFreeBlockIndex(ftEnt.seekPtr, newLocation);

		// error on write of nullptr
		if (testPtr == -3)
		{
			short freeBlock = (short) this.superblock.nextBlock();

			// indirect pointer is empty
			if (!ftEnt.inode.setIndexBlock(freeBlock))
				return -1;

			// check block pointer error
			if (ftEnt.inode.getFreeBlockIndex(ftEnt.seekPtr, newLocation) != 0)
				return -1;

		}
		// Error on write of unused and used
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
    	if (ftEnt.inode.count != 1)
		{
			return false;
		}

		for (short blockId = 0; blockId < ftEnt.inode.directSize; blockId++)
		{
			if (ftEnt.inode.direct[blockId] != -1)
			{
				superblock.returnBlock(blockId);
				ftEnt.inode.direct[blockId] = -1;
			}
		}

		byte[] data = ftEnt.inode.freeIndirect();

		if (data != null)
		{
			short blockId;
			while((blockId = SysLib.bytes2short(data, 0)) != -1)
			{
				superblock.returnBlock(blockId);
			}
		}
		ftEnt.inode.toDisk(ftEnt.iNumber);
		return true;
    }

	 //---------------------- boolean delete( String ) ---------------------
	/** Deletes the file specified by given fileName.
	 * If the file is currently open, it is not destroyed
	 * until the last open on it is closed, but new attempts to open it will fail.
	 */
	public boolean delete(String filename)
	{
		FileTableEntry tcb = open(filename, "w"); // Grabs the iNode(aka tcb)

		if(directory.ifree(tcb.iNumber) && close(tcb)) // frees iNode and closes successfully
			return true;   // deletion successful

		return false;     // deletion unsuccessful
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
