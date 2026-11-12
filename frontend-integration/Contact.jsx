import React, { useState, useEffect } from 'react';
import { CONTACT_DETAILS, SITE, waLink } from '../data/siteData';
import WhatsAppIcon from './WhatsAppIcon';
import useReveal from '../hooks/useReveal';
import api from '../api/api';
import './Contact.css';

export default function Contact() {
  const lRef = useReveal();
  const rRef = useReveal();
  const [status, setStatus] = useState('idle');
  const [form, setForm] = useState({ name:'', phone:'', email:'', date:'', service:'', message:'' });

  const handleChange = e => setForm(f => ({ ...f, [e.target.name]: e.target.value }));

  const [avail, setAvail] = useState(null);   // { count, booked } for the chosen date
  const [err, setErr] = useState('');

  // "Book Date" / "Check Availability" buttons fire this event (see utils/bookDate.js)
  useEffect(() => {
    const h = (e) => setForm(f => ({ ...f, ...(e.detail || {}) }));
    window.addEventListener('prefill-inquiry', h);
    return () => window.removeEventListener('prefill-inquiry', h);
  }, []);

  // live availability check whenever the date changes
  useEffect(() => {
    if (!form.date) { setAvail(null); return; }
    let on = true;
    api.checkDate(form.date).then(r => on && setAvail(r)).catch(() => on && setAvail(null));
    return () => { on = false; };
  }, [form.date]);

  // Saves the enquiry in the backend (it appears in the admin dashboard).
  const handleSubmit = async e => {
    e.preventDefault();
    setErr(''); setStatus('sending');
    try {
      await api.createInquiry({ ...form });
      setStatus('sent');
      setTimeout(() => { setStatus('idle'); setAvail(null); setForm({ name:'', phone:'', email:'', date:'', service:'', message:'' }); }, 6000);
    } catch (x) { setErr(x.message); setStatus('idle'); }
  };

  return (
    <section className="contact" id="contact">
      <div className="con-info rl" ref={lRef}>
        <p className="sec-label">Get In Touch</p>
        <h2 className="sec-title">Let's Create<br/><em>Magic Together</em></h2>
        <p className="sec-body">Every great love story deserves to be beautifully documented. Reach out to check availability and let us craft your visual legacy.</p>
        {CONTACT_DETAILS.map(d => (
          <div key={d.label} className="con-row">
            <div className="con-icon">{d.icon}</div>
            <div>
              <div className="con-label">{d.label}</div>
              <div className="con-val">{d.value}</div>
            </div>
          </div>
        ))}
        <div className="con-socials">
          <a href={SITE.instagram} className="con-soc" aria-label="Instagram" target="_blank" rel="noopener noreferrer">I</a>
          <a href={SITE.facebook}  className="con-soc" aria-label="Facebook"  target="_blank" rel="noopener noreferrer">F</a>
          <a href={SITE.youtube}   className="con-soc" aria-label="YouTube"   target="_blank" rel="noopener noreferrer">Y</a>
          <a href={waLink()}       className="con-soc con-soc-wa" aria-label="WhatsApp" target="_blank" rel="noopener noreferrer"><WhatsAppIcon size={18} /></a>
        </div>
      </div>
      <div className="con-form-wrap rr" ref={rRef}>
        <p className="sec-label">Book Your Date</p>
        <h2 className="sec-title" style={{fontSize:'clamp(1.6rem,3vw,2.2rem)'}}>Tell Us Your <em>Story</em></h2>
        {status === 'sent' ? (
          <div className="form-success">
            <div className="success-icon">✓</div>
            <h3>Enquiry received!</h3>
            <p>Tejas will check the date and confirm on WhatsApp shortly.</p>
          </div>
        ) : (
          <form className="con-form" onSubmit={handleSubmit}>
            <div className="form-row2">
              <div className="form-group">
                <label>Your Name</label>
                <input name="name" value={form.name} onChange={handleChange} placeholder="Full name" required />
              </div>
              <div className="form-group">
                <label>Phone</label>
                <input name="phone" value={form.phone} onChange={handleChange} placeholder="+91 00000 00000" required />
              </div>
            </div>
            <div className="form-row2">
              <div className="form-group">
                <label>Email</label>
                <input type="email" name="email" value={form.email} onChange={handleChange} placeholder="your@email.com" />
              </div>
              <div className="form-group">
                <label>Event Date</label>
                <input type="date" name="date" value={form.date} onChange={handleChange} />
                {avail && (
                  <small style={{ color: avail.booked ? '#b3261e' : '#2e7d32' }}>
                    {avail.booked ? 'This date is already booked - please pick another.' :
                     avail.count > 0 ? `${avail.count} enquiry(ies) already for this date - still open.` : 'Date is available.'}
                  </small>
                )}
              </div>
            </div>
            <div className="form-group">
              <label>Service Required</label>
              <select name="service" value={form.service} onChange={handleChange}>
                <option value="">Select a service</option>
                <option>Wedding Photography</option>
                <option>Cinematic Wedding Film</option>
                <option>Pre-Wedding Shoot</option>
                <option>Fashion & Model Shoot</option>
                <option>Portrait Session</option>
                <option>Brand & Jewellery Shoot</option>
                <option>Maternity Photography</option>
                <option>Engagement Session</option>
                <option>Destination Wedding</option>
                <option>Photo + Film Package</option>
              </select>
            </div>
            <div className="form-group">
              <label>Tell Us More</label>
              <textarea name="message" value={form.message} onChange={handleChange} placeholder="Share your vision, venue, guest count or any special requests..." />
            </div>
            {err && <p style={{ color: '#b3261e' }}>{err}</p>}
            <button type="submit" className="btn1" disabled={status === 'sending' || (avail && avail.booked)}>
              {status === 'sending' ? 'Sending…' : 'Send Enquiry →'}
            </button>
          </form>
        )}
      </div>
    </section>
  );
}
