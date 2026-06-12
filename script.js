// Hide loader after page load
window.addEventListener('load', () => {
  const loader = document.getElementById('loader');
  setTimeout(() => {
    loader.classList.add('hide');
    setTimeout(() => { loader.style.display = 'none'; }, 650);
  }, 400);
});

// Navbar scroll effect
const navbar = document.getElementById('navbar');
window.addEventListener('scroll', () => {
  navbar.classList.toggle('scrolled', window.scrollY > 50);
});

// Mobile nav toggle
document.getElementById('navToggle').addEventListener('click', () => {
  document.getElementById('navLinks').classList.toggle('open');
});

// Close mobile nav on link click
document.querySelectorAll('.nav-links a').forEach(link => {
  link.addEventListener('click', () => {
    document.getElementById('navLinks').classList.remove('open');
  });
});

// Menu category tabs
document.querySelectorAll('.cat-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    const cat = btn.dataset.cat;
    document.querySelectorAll('.cat-btn').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.menu-panel').forEach(p => p.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById(cat).classList.add('active');
  });
});

// Order form → WhatsApp
document.getElementById('orderForm').addEventListener('submit', function(e) {
  e.preventDefault();
  const items   = document.getElementById('orderItems').value.trim();
  const address = document.getElementById('orderAddress').value.trim();
  const phone   = document.getElementById('orderPhone').value.trim();
  const payment = document.getElementById('orderPayment').value;
  const agreed  = document.getElementById('orderConfirm').checked;
  const errorEl = document.getElementById('formError');

  if (!items || !address || !phone || !payment || !agreed) {
    errorEl.classList.add('show');
    errorEl.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    return;
  }
  errorEl.classList.remove('show');

  const msg =
    `Hola, quisiera hacer un pedido 🍽️\n\n` +
    `1️⃣ Pedido: ${items}\n` +
    `2️⃣ Dirección (con referencia): ${address}\n` +
    `3️⃣ Teléfono: ${phone}\n` +
    `4️⃣ Medio de pago: ${payment}\n\n` +
    `⚠️ Entiendo que una vez confirmado el pedido NO SE PUEDE CANCELAR.`;

  window.open('https://wa.me/573014790406?text=' + encodeURIComponent(msg), '_blank');
});

// Intersection Observer for fade-in animations
const observerOptions = { threshold: 0.12 };
const observer = new IntersectionObserver((entries) => {
  entries.forEach(entry => {
    if (entry.isIntersecting) {
      entry.target.style.opacity = '1';
      entry.target.style.transform = 'translateY(0)';
    }
  });
}, observerOptions);

document.querySelectorAll('.info-card, .menu-card, .gal-item, .contact-item').forEach(el => {
  el.style.opacity = '0';
  el.style.transform = 'translateY(24px)';
  el.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
  observer.observe(el);
});
