import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class jpegDisplay extends JpegDisplayDemo{
    BufferedImage setTransparency(BufferedImage back, BufferedImage foreground, List<Integer> list) {
        int width = foreground.getWidth();
        int height = foreground.getHeight();
        int sliceHeight = height / 8; // Giả sử ảnh chia thành 16 slice

        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = combined.createGraphics();

        g.drawImage(foreground, 0, 0, null); // Vẽ ảnh foreground ban đầu
        for (int slice : list) { // Với mỗi slice bị mất
            int yStart = slice * sliceHeight;
            int yEnd = Math.min(yStart + sliceHeight, height);

            for (int y = yStart; y < yEnd; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = back.getRGB(x, y); // Lấy pixel từ background
                    combined.setRGB(x, y, pixel); // Sao chép pixel đó vào ảnh kết hợp
                }
            }
        }

        g.dispose();
        return combined; // Trả về ảnh đã được xử lý
    }
}
