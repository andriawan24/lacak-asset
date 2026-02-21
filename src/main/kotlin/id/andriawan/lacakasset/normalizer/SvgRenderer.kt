package id.andriawan.lacakasset.normalizer

import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.PNGTranscoder
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.imageio.ImageIO

class SvgRenderer {

    fun renderSvg(inputStream: InputStream, width: Int, height: Int): BufferedImage {
        val transcoder = PNGTranscoder()
        transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, width.toFloat())
        transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, height.toFloat())

        val input = TranscoderInput(inputStream)
        val outputStream = ByteArrayOutputStream()
        transcoder.transcode(input, TranscoderOutput(outputStream))

        return ImageIO.read(ByteArrayInputStream(outputStream.toByteArray()))
    }

    fun renderSvgFromString(svgContent: String, width: Int, height: Int): BufferedImage {
        return renderSvg(ByteArrayInputStream(svgContent.toByteArray(Charsets.UTF_8)), width, height)
    }
}
