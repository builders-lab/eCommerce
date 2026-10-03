/* OmniCart — minimal JS. Backend handles all real behaviour. */

// Mobile menu toggle
const menuButton = document.querySelector('.menu-toggle');
const navLinks = document.querySelector('.nav-links');

if (menuButton && navLinks) {
  menuButton.addEventListener('click', function () {
    navLinks.classList.toggle('open');
  });
}
