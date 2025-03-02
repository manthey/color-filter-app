Color Filter Android App
========================

This app shows either a zoomable live camera view or lets the user select an image. 
The camera or image can be shown either filtered by hue or basic color term.

Views
-----

- **Off**: Shows the original live camera stream or image.

- **Include**: Only parts of the image that match the hue or basic color term and
  are above the saturation and luminance thresholds are shown.  Other areas are
  black.

- **Exclude**: The parts of the image that match the filter are covered with white.
  The other areas are the original image.

- **Binary**: The parts of the image that match the filter are white.  The remainder
  is black.

- **Saturation**: The parts of the image that match the filter are shaded based on
  saturation, where white is fully saturated and black is desaturated.  The
  remainder is black.

Modes
-----

- **HSV**: The filter is based on hue angle, ranging from the hue width below the
  hue angle to the hue width above the hue angle.  The color names are based on the
  fully saturated colors used in css color specifications, which means the colors
  include uncommon words and are inaccurate for anything other than high saturations
  and luminances.

- **BCT**: The filter is based on basic color terms.  There is a color map for all
  colors and intensities to basic color terms.  Currently the only term map is based
  on Lindsey, D. T., and A. M. Brown. 2014. "The Color Lexicon of American
  English." Journal of Vision. Association for Research in Vision and
  Ophthalmology (ARVO). https://doi.org/10.1167/14.2.17.  Mostly this drawn from 
  Figure 9 as accessed via https://jov.arvojournals.org/article.aspx?articleid=2121523
  using the 20 basic color terms.  Internally a blur filter is applied to try to
  reduce point variation in colors.
