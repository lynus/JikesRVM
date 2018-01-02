import java.util.Random;
public class testgc {
	static Object[] buf = new Object[20000];
	public static void main(String[] args) {
		System.gc();
		int i = 0;
		int k = 0;
		while (i < 10) {
			k++;
			if (k % 6 == 0)
				k=1;
			System.out.println(k);
			for(int j=0;j < 20000; j++) {
				buf[j] = new byte[k * 128];
			}
			System.gc();
			i++;
		}	
	}
}
