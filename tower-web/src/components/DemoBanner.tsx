// Shown on every page of the public demo, and deliberately not dismissible.
//
// Tower's whole claim is that it reports facts with provenance — which
// environment, observed when, from what source. A demo populated with invented
// observations that did not say so would undercut the exact property it exists
// to demonstrate. The banner is the price of showing the thing at all.
export default function DemoBanner() {
  return (
    <div className="demo-banner" role="note">
      <strong>Demo — all data on this page is fabricated.</strong> Nothing here was observed by
      anyone and no release described here exists. Changes you make are held in your browser only
      and disappear when you reload. The real Tower runs locally against your own data.
    </div>
  );
}
