/** ----------------------- Directory.java --------------------------
 * @author Camila Valdebenito
 * @author Connor Riley Shabro
 * @author Jeffrey Murray Jr
// *
// * DESCRIPTION:
// * This class   
// *  
// * ASSUMPTIONS:
// */
public class Directory 
{
   private static int maxChars = 30; // max characters of each file name
   private static int MAX_BYTES = 60; // max bytes of each file name in Java


   // Directory entries
   private int fsize[];        // each element stores a different file size.
   private char fnames[][];    // each element stores a different file name.
   private int directorySize;   // each element stores the size of the directory

   // Directory constructor
   public Directory( int maxInumber ) 
   {      
      directorySize = maxInumber;
      fsize = new int[directorySize];          // directorySize = max files
      
      for ( int i = 0; i < directorySize; i++ ) 
      {
         fsize[i] = 0; 
      }                                      // all file size initialized to 0
      fnames = new char[directorySize][maxChars];
      String root = "/";                     // entry(inode) 0 is "/"
      fsize[0] = root.length( );             // fsize[0] is the size of "/".
      root.getChars( 0, fsize[0], fnames[0], 0 ); // fnames[0] includes "/"
   }

   // initializes the Directory instance with this data[]
   // assumes data[] received directory information from disk
   // returns -1 if this data[] is invalid 
   public int bytes2directory( byte data[] ) 
   {
      // checks for invalid input
      if(data == null || data.length == 0)  // if data[] is invalid returns -1   
      {   
         return -1; 
      }
      
      int offset = 0;
      for(int i = 0; i < directorySize; i++) 
      {
         fsize[i] = SysLib.bytes2int(data, offset);  // data conversion from bytes to int, returns int
         offset += 4;                                // an int has size of 4 bytes
      }
      
         // initializes the Directory instance with this data[]
      for(int i = 0; i < directorySize; i++)
      {
         String fileName = new String(data, offset, MAX_BYTES);
         fileName.getChars(0, fsize[i], fnames[i], 0);
         offset += MAX_BYTES;
      }
      
      printDir();
      return 1;
       
   }

 //   // converts and return Directory information into a plain byte array
//    // this byte array will be written back to disk
//    // note: only meaningfull directory information should be converted
//    // into bytes.
//    public byte[] directory2bytes( ) 
//    {
//       //byte[] directory = new byter[directorySize * ]
// 
//       
//    }
// 
//    public short ialloc( String filename ) 
//    {
//       // filename is the one of a file to be created.
//       // allocates a new inode number for this filename
//    }

   // deallocates this inumber (inode number)
   // the corresponding file will be deleted.
   // Assumes that the given iNumber is positive 
   public boolean ifree( short iNumber ) 
   {
      if(iNumber >= 0 && iNumber < maxChars && fsize[iNumber] > 0) 
      {
         for(int i = 0; i < maxChars; i++) 
         {
            fnames[iNumber][i] = 0; // remove the fileName
         }
         fsize[iNumber] = 0;       // fileSize initialized to 0 (default)
         return true;               // file found
      }
      
      return false;                 // file not found 

   }
   
 //   // returns the inumber corresponding to this filename
//    public short namei( String filename ) 
//    {
//    
//       
//    }
   
   private void printDir(){
     for (int i = 0; i < directorySize; i++){
         SysLib.cout(i + ":  " + fsize[i] + " bytes - ");
         for (int j = 0; j < maxChars; j++){
             SysLib.cout(fnames[i][j] + " ");
         }
         SysLib.cout("\n");
     }
    }
}
