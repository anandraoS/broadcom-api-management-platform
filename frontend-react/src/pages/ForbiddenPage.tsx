import { Link } from 'react-router-dom';

export default function ForbiddenPage() {
  return (
    <>
      <h1 className="page-title">Access denied</h1>
      <p>Your role does not include this area. The server enforces this independently of what the menu shows.</p>
      <Link to="/dashboard">Back to the dashboard</Link>
    </>
  );
}
