/**  ========================================== Directory.java ==============================================
 * @author Camila Valdebenito
 * @author Connor Riley Shabro
 * @author Jeffrey Murray Jr
 * 
 * PURPOSE
 * Contain and manage files with two arrays (fsize and fname)
 * 
 * TODO @cami plz add comments
 * 
 * The main purpose of directory is to contain and manage the “files” that are being dealt with. Directory accomplishes
 * this by means of creating two arrays fsize and fname.
 *
 * fsize is used to contain the sizes of these files in their respective locations. size can be visualized as a simple list
 * of numbers representing the different sizes of file stored int he fname array. Upon Directory’s initialization, the
 * constructor is handed an int called “maxInumber” which is the maximun stored files that the fsize array will hold.
 * Fname is used to contain the “files” that the directory is holding.
 *
 * The Directory gets broken up into smaller functions to do things like reading data from a  byte array into the directory
 * and writing from the directory back to the byte array.
 */

public class Directory {
    private static int maxChars = 30; // max characters of each file name
    private static int MAX_BYTES = 60;
    private static int ALLOC_BYTE = 64;

    // Directory entries
    private int fsize[];        // each element stores a different file size.
    private int directorySize;  // size of directory
    private char fnames[][];    // each element stores a different file name.

    //---------------------- Directory( int ofSize ) ---------------------
    /** Default Constructor
     *
     * @param ofSize int
     */
    public Directory( int ofSize )
    { // directory constructor
        fsize = new int[ofSize];     // maxInumber = max files
        for ( int i = 0; i < ofSize; i++ )
            fsize[i] = 0;                 // all file size initialized to 0
        directorySize = ofSize;
        fnames = new char[ofSize][maxChars];
        String root = "/";                // entry(inode) 0 is "/"
        fsize[0] = root.length( );        // fsize[0] is the size of "/".
        root.getChars( 0, fsize[0], fnames[0], 0 ); // fnames[0] includes "/"
    }

    //---------------------- bytes2directory( byte data[] ) ---------------------
    /** 
     * Initializes the Directory instance with this data[]
     * Assumes data[] received directory information from disk
     * @param data byte[]
     */
    public void bytes2directory( byte data[] )
    {
        int offset = 0;
        for (int i = 0; i < directorySize; i++)
        {
            fsize[i] = SysLib.bytes2int(data, offset);
            offset += 4;
        }
        for (int i = 0; i < directorySize; i++)
        {
            String temp = new String(data, offset, MAX_BYTES);
            temp.getChars(0, fsize[i], fnames[i], 0);
            offset += MAX_BYTES;
        }
    }

    //--------------------------- directory2bytes( ) --------------------------
    /**
     * Converts and return Directory information into a plain byte array
     * @return byte[] which represents the directory
     */
    public byte[] directory2bytes( )
    {
        byte [] dir = new byte[ALLOC_BYTE * directorySize];
        int offset = 0;

        for (int i = 0; i < directorySize; i++)
        {
            SysLib.int2bytes(fsize[i], dir, offset);
            offset += 4;
        }
        for (int i = 0; i < directorySize; i++)
        {
            String temp = new String(fnames[i], 0, fsize[i]);
            byte [] bytes = temp.getBytes();
            System.arraycopy(bytes, 0, dir, offset, bytes.length);
            offset += MAX_BYTES;
        }
        return dir;
    }

    //--------------------------- ialloc( String filename ) --------------------------
    /** 
     * Allocates a new iNode number for the given filename
     *
     * @param filename
     * @return the iNode number that corresponds to filename
     */
    public short ialloc( String filename )
    {
        // filename is the one of a file to be created.
        for (short i = 0; i < directorySize; i++)
        {
            if (fsize[i] == 0)
            {
                // allocates a new inode number for this filename
                int file = filename.length() > maxChars ? maxChars : filename.length();
                fsize[i] = file;
                filename.getChars(0, fsize[i], fnames[i], 0);
                return i;
            }
        }
        return -1;
    }

    //--------------------------- ifree( short iNumber ) --------------------------
    /**
     *  Frees the file with the given iNumber
     * 
     * @param iNumber number
     * @return boolean variable to determine the success of the operation
     */
    public boolean ifree( short iNumber ) {
        if(iNumber < maxChars && fsize[iNumber] > 0){      //If number is valid
            fsize[iNumber] = 0;                            //Mark to be deleted
            return true;                                 //File was found
        } else {
            return false;                                 //File not found
        }
    }

    
    //--------------------------- namei( String filename ) --------------------------
    /** 
     * Returns the iNode number that corresponds to the given filename
     * 
     * @param filename
     * @return the iNode number that corresponds to the given filename
     */
    public short namei( String filename )
    {
        for (short i = 0; i < directorySize; i++){
            if (filename.length() == fsize[i]){
                String temp = new String(fnames[i], 0, fsize[i]);
                if(filename.equals(temp)){
                    return i;
                }
            }
        }
        return -1;
    }

    //--------------------------- printDir() --------------------------
    /** Print Dir
     * Helper method that prints out the directory
     * TESTING ONLY
     */
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
