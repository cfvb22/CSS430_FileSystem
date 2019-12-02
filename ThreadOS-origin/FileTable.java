import java.util.Vector;

import javax.lang.model.util.ElementScanner6;

/** ----------------------- FileSystem.java --------------------------
 * @author Jeffrey Murray Jr
 * @author Camila Valdebenito
 * @author Connor Riley Shabro
 *
 * SUMMARY
 * Stores a FileTable -> Vector<FileTableEntry>
 * Each file table represents one file descriptor
 *
 * PURPOSE
 * Create a new FTE and adds to FileTable
 * Removes a FTE from FileTable
 *
 */

public class FileTable {

   public final int UNUSED = 0;
   public final int USED = 1;
   public final int READ = 2;
   public final int WRITE = 3;

   // the actual entity of this file table
   private Vector<FileTableEntry> FileTable;

   // the root directory
   private Directory dir;

   public FileTable( Directory directory ) {
      // instantiate a file (structure) table
      FileTable = new Vector<FileTableEntry>( );

      // receive a reference to the Directory
      dir = directory;
   }

   // ---------------------------- falloc ----------------------------
   /**
    *! falloc
    ** Creating a FileTableEntry for a requested file
    * Ensures: file is not open for write by more than one thread
    *          file is open for write -> others cannot read file
    *               if already opened -> wait until thread closes FTE
    *          file does not exist    -> opened in write mode
    *
    * @param filename target file in Directory
    * @param mode "r"/"w"/"a" (read/write/append)
    * @return null/new FTE
    */
   public synchronized FileTableEntry falloc( String filename, String mode ) {
      // allocate a new file (structure) table entry for this file name
      Inode inode = null;
      short iNumber = -1;

      while(true) {
         // allocate/retrieve and register the corresponding inode using dir
         iNumber = (filename.equals("/")) ? (short) 0: dir.namei(filename);

         // File Exists in Directory
         if(iNumber >= 0)
         {
            inode = new Inode(iNumber); // assign iNode

            // bad file check ( iNode is out of bounds! )
            if(inode.flag < UNUSED || inode.flag > READ) { return null; }

            // Open file for read
            if(mode == "r")
            {
               // READ || USED || UNUSED
               if(inode.flag < WRITE)
               {
                  inode.flag = READ;
                  break;
               }
               // WRITE
               else
               {
                  // wait for other thread to finish writing to file
                  try { wait(); }
                  catch (InterruptedException e) { }
               }
            }
            // Open file for writing/append
            else
            {
               // USED || UNUSED
               if(inode.flag <= USED)
               {
                  inode.flag = WRITE;
                  break;
               }
               else
               {
                  // wait for other thread to finish read/write to file
                  try { wait(); }
                  catch (InterruptedException e) { }
               }
            }
         }
         // New File (write/append)
         else if(!(mode == "r"))
         {
            iNumber = dir.ialloc(filename);
            inode = new Inode(iNumber);
            inode.flag = WRITE;
            break;
         }
         // Trying to read a file that does not exist
         else { return null; }
      }
      // increment this inode's count
      inode.count++;
      // immediately write back this inode to the disk
      inode.toDisk(iNumber);
      // return a reference to this file (structure) table entry
      FileTableEntry entry = new FileTableEntry(inode, iNumber, mode);
      FileTable.addElement(entry);
      return entry;
   }

   // ---------------------------- ffree ----------------------------
   /**
    *! Closes and Removes FileTableEntry from FileTable
    * If thread was last user (1), wakes up another thread with reading status
    * Otherwise, all threads if file was written to
    * @param entry <FileTableEntry> to be freed
    * @return success/fail
    */
   public synchronized boolean ffree( FileTableEntry e ) {
      // receive a file table entry reference
      Inode inode = new Inode(e.iNumber);
      // Check to see if entry is in the FileTable
      if(FileTable.remove(e))
      {
         if(inode.flag == READ)
         {
            if(inode.count == 1)
            {
               // free this file table entry.
               notify();
               inode.flag = USED;
            }
         }
         else if(inode.flag == WRITE)
         {
            inode.flag = USED;
            notifyAll();
         }
         // Decrease count of users of entry file
         inode.count--;
         // save the corresponding inode to the disk
         inode.toDisk(e.iNumber);
         // return true if this file table entry found in my table
         return true;
      }
      return false;
   }
   // ---------------------------- fempty ----------------------------
   /**
    * @return FileTable.isEmpty()
    */
   public synchronized boolean fempty( ) {
      return FileTable.isEmpty( );  // return if table is empty
   }                            // should be called before starting a format
}
