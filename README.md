Color Filter Android App
========================

This app is intended to help colorblind people determine what color terms are 
used by people with color vision.  The basic use is to point the camera at an
object and cycle through the color terms to determine which one has the
greatest response.  Some terms, like orange and brown, are quite light
sensitive.  Further, some basic color terms vary by speaker (e.g., teal can be
treated as synonymous with aqua, turquoise, or cyan).

The app shows either a zoomable live camera view or lets the user select an
image.  The camera or image can be shown either filtered by hue or basic color
term.

Views
-----

- **Off**: Shows the original live camera stream or image.

- **Include**: Only parts of the image that match the hue or basic color term
  and are above the saturation and luminance thresholds are shown.  Other areas
  are black.

- **Exclude**: The parts of the image that match the filter are covered with
  white.  The other areas are the original image.

- **Binary**: The parts of the image that match the filter are white.  The
  remainder is black.

- **Saturation**: The parts of the image that match the filter are shaded based
  on saturation, where white is fully saturated and black is desaturated.  The
  remainder is black.

Modes
-----

- **HSV**: The filter is based on hue angle, ranging from the hue width below
  the hue angle to the hue width above the hue angle.  The color names are
  based on the fully saturated colors used in css color specifications, which
  means the colors include uncommon words and are inaccurate for anything other
  than high saturations and luminances.

- **BCT**: The filter is based on basic color terms.  There is a color map for
  all colors and intensities to basic color terms.  Internally a blur filter is
  applied to try to reduce point variation in colors.


The Color Term Map
------------------

Currently the only term map is based on Lindsey, D. T., and A. M. Brown. 2014.
"The Color Lexicon of American English." Journal of Vision. Association for 
Research in Vision and Ophthalmology (ARVO). https://doi.org/10.1167/14.2.17.  
Mostly this drawn from Figure 9 as accessed via
https://jov.arvojournals.org/article.aspx?articleid=2121523 using the 20 basic 
color terms.

This was processed by computing the Munsell color values listed in the chart
mentioned in the paper.  Each of the color response maps in Figure 9 was
digitized to extract probabilities of of 0 to 1 for each of the 320
corresponding Munsell colors.  Where there was a response greater than 0.1, the
color term with the strongest response was associated with that Munsell color.
This was supplemented with manually encoded values for the neutrals and with
the extreme values for the sRGB color space for black, white, red, green, blue,
teal, purple, and yellow.  These colors were all converted to L\*a\*b\*.

For the entire 256 x 256 x 256 sRGB 8-bit color cube, the L\*a\*b\* values were
computed at each point.  Each point in the color cube was assigned the color 
term closest to a L\*a\*b\* value that had been found in the previous step.  
This color cube is written out as a grayscale PNG where pixel values are the 
index into the terms list.  It is written as a grid of 16 x 16 images, each
256 x 256 pixels ordered from red = 0 at the top left, red = 1 just to the 
right of that, all the way to red = 255 at the bottom right.  In each sub
image, blue runs left (0) to right (255) and green runs top (0) to bottom 
(255).  The term order is listed by the processing program and must match the
PNG.  The PNG is also written out in a palettized form to make it easier for
color-sighted people to check the results.

